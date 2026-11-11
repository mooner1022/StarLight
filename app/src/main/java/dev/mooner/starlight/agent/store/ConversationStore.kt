package dev.mooner.starlight.agent.store

import android.util.AtomicFile
import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.model.CallResults
import dev.mooner.starlight.agent.model.Conversation
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.model.RunState
import dev.mooner.starlight.agent.model.plainPreview
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException

private val LOG = LoggerFactory.logger("AgentStore")

/** What the conversation list shows, kept in an index so that the list doesn't read every conversation. */
@Serializable
data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val state: RunState,
    val project: String? = null,
    val preview: String = "",
    val provider: String? = null,
    val model: String? = null,
)

/**
 * Conversations as JSON files in the app's private storage, one per conversation, written atomically. The ones
 * used last stay in memory; [summaries] is what lists observe.
 */
class ConversationStore(root: File) {

    private val conversationsDir = File(root, "conversations").apply { mkdirs() }
    private val indexFile = AtomicFile(File(root, "index.json"))
    /** Every change is written before it is cached, so a conversation dropped from here is read back as it was. */
    private val cache = object : LinkedHashMap<String, Conversation>(MAX_CACHED, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Conversation>?) = size > MAX_CACHED
    }
    private val lock = Any()
    /** Unreadable files already reported in the log, so each is reported once. */
    private val reportedUnreadable: MutableSet<String> = hashSetOf()

    private val mSummaries = MutableStateFlow(loadIndex())
    /** Newest first. */
    val summaries: StateFlow<List<ConversationSummary>> = mSummaries.asStateFlow()

    private val mChanges = MutableStateFlow(0L)
    /** Goes up with every change to any conversation; open chat screens read theirs again with [get]. */
    val changes: StateFlow<Long> = mChanges.asStateFlow()

    fun get(id: String): Conversation? =
        cached(id) ?: synchronized(lock) {
            cached(id) ?: read(id)?.also(::remember)
        }

    /**
     * Whether [id]'s file is there but can't be read, such as one written by a newer version of the app. It is kept
     * as it is: [get] gives null for it as for a conversation that isn't there, but [save] won't write over it.
     */
    fun isUnreadable(id: String): Boolean =
        synchronized(lock) {
            cached(id) == null && fileOf(id).isFile && read(id) == null
        }

    fun save(conversation: Conversation) {
        synchronized(lock) {
            if (isUnreadable(conversation.id))
                throw IOException("Conversation ${conversation.id} can't be read and is left as it is")
            val file = AtomicFile(fileOf(conversation.id))
            val out = file.startWrite()
            try {
                out.write(AgentJson.encodeToString(conversation).toByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
            remember(conversation)
            val summary = summaryOf(conversation)
            // Many changes, such as an approval or the run state, leave what the list shows as it was.
            if (mSummaries.value.firstOrNull { it.id == conversation.id } != summary) {
                mSummaries.update { list -> (listOf(summary) + list.filter { it.id != conversation.id }).sortedByDescending { it.updatedAt } }
                writeIndex()
            }
        }
        mChanges.update { it + 1 }
    }

    /** Applies [block] to the stored conversation and saves the result. */
    fun update(id: String, block: (Conversation) -> Conversation): Conversation {
        synchronized(lock) {
            val current = get(id) ?: throw IllegalArgumentException("No conversation $id")
            val updated = block(current)
            save(updated)
            return updated
        }
    }

    fun delete(id: String) {
        synchronized(lock) {
            synchronized(cache) { cache.remove(id) }
            fileOf(id).delete()
            mSummaries.update { list -> list.filter { it.id != id } }
            writeIndex()
        }
        mChanges.update { it + 1 }
    }

    /**
     * Marks runs the process didn't finish, after the app was killed in the middle of one, and closes the calls they
     * left open: resuming then lets the model decide what to do again rather than repeat a call that may have been
     * what stopped the app.
     */
    fun markInterrupted() {
        val now = System.currentTimeMillis()
        for (summary in mSummaries.value.filter { it.state.isActive }) {
            val conversation = get(summary.id) ?: continue
            val messages = CallResults.closeInterrupted(conversation.messages, waited = conversation.state == RunState.WAITING_APPROVAL, now)
            save(conversation.copy(state = RunState.INTERRUPTED, messages = messages))
        }
    }

    private fun cached(id: String): Conversation? =
        synchronized(cache) { cache[id] }

    private fun remember(conversation: Conversation) {
        synchronized(cache) { cache[conversation.id] = conversation }
    }

    private fun read(id: String): Conversation? {
        val file = fileOf(id)
        if (!file.isFile)
            return null
        return runCatching { AgentJson.decodeFromString<Conversation>(AtomicFile(file).readFully().decodeToString()) }
            .onFailure { e ->
                if (synchronized(reportedUnreadable) { reportedUnreadable.add(id) })
                    LOG.warn { "Conversation $id can't be read, so it is left as it is: $e" }
            }
            .getOrNull()
    }

    private fun fileOf(id: String): File {
        require(id.matches(ID_REGEX)) { "Invalid conversation id: $id" }
        return File(conversationsDir, "$id.json")
    }

    private fun loadIndex(): List<ConversationSummary> {
        val fromIndex = runCatching {
            AgentJson.decodeFromString<List<ConversationSummary>>(indexFile.readFully().decodeToString())
        }.getOrNull()
        if (fromIndex != null)
            return fromIndex.sortedByDescending { it.updatedAt }
        // No index yet, or a damaged one: rebuild it from the files.
        return conversationsDir.listFiles { file -> file.extension == "json" }.orEmpty()
            .mapNotNull { file -> read(file.nameWithoutExtension)?.let(::summaryOf) }
            .sortedByDescending { it.updatedAt }
    }

    private fun writeIndex() {
        val out = indexFile.startWrite()
        try {
            out.write(AgentJson.encodeToString(mSummaries.value).toByteArray())
            indexFile.finishWrite(out)
        } catch (e: Exception) {
            // The list still has it; the index is rebuilt from the files if it is lost.
            LOG.warn { "Couldn't write the conversation index: $e" }
            indexFile.failWrite(out)
        }
    }

    private fun summaryOf(conversation: Conversation): ConversationSummary {
        val preview = conversation.messages.asReversed().firstNotNullOfOrNull { message ->
            when (message.role) {
                Role.TOOL -> null
                else -> message.parts.filterIsInstance<Part.Text>().lastOrNull()?.text
            }
        }.orEmpty()
        return ConversationSummary(
            id = conversation.id,
            title = conversation.title,
            updatedAt = conversation.updatedAt,
            state = conversation.state,
            project = conversation.project,
            preview = preview.plainPreview().take(PREVIEW_LENGTH),
            provider = conversation.provider,
            model = conversation.model,
        )
    }

    private companion object {
        const val PREVIEW_LENGTH = 140
        /** Conversations kept in memory; a run reads its own on every step, the rest come back from disk. */
        const val MAX_CACHED = 8
        val ID_REGEX = Regex("^[A-Za-z0-9_-]+$")
    }
}
