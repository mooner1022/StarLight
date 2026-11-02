package dev.mooner.starlight.agent.store

import android.util.AtomicFile
import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.model.Conversation
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.model.RunState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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
 * Conversations as JSON files in the app's private storage, one per conversation, written atomically. Open
 * conversations stay in memory; [summaries] is what lists observe.
 */
class ConversationStore(private val root: File) {

    private val conversationsDir = File(root, "conversations").apply { mkdirs() }
    private val indexFile = AtomicFile(File(root, "index.json"))
    private val cache = ConcurrentHashMap<String, Conversation>()
    private val lock = Any()

    private val mSummaries = MutableStateFlow(loadIndex())
    /** Newest first. */
    val summaries: StateFlow<List<ConversationSummary>> = mSummaries.asStateFlow()

    private val mChanges = MutableStateFlow<Map<String, Conversation>>(emptyMap())
    /** Conversations as they change, for open chat screens. */
    val changes: StateFlow<Map<String, Conversation>> = mChanges.asStateFlow()

    fun get(id: String): Conversation? =
        cache[id] ?: synchronized(lock) {
            cache[id] ?: read(id)?.also { cache[id] = it }
        }

    fun save(conversation: Conversation) {
        synchronized(lock) {
            cache[conversation.id] = conversation
            val file = AtomicFile(fileOf(conversation.id))
            val out = file.startWrite()
            try {
                out.write(AgentJson.encodeToString(conversation).toByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
            val summary = summaryOf(conversation)
            mSummaries.update { list -> (listOf(summary) + list.filter { it.id != conversation.id }).sortedByDescending { it.updatedAt } }
            writeIndex()
        }
        mChanges.update { it + (conversation.id to conversation) }
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
            cache.remove(id)
            fileOf(id).delete()
            mSummaries.update { list -> list.filter { it.id != id } }
            writeIndex()
        }
        mChanges.update { it - id }
    }

    /** Marks runs the process didn't finish, after the app was killed in the middle of one. */
    fun markInterrupted() {
        for (summary in mSummaries.value.filter { it.state.isActive }) {
            val conversation = get(summary.id) ?: continue
            save(conversation.copy(state = RunState.INTERRUPTED))
        }
    }

    private fun read(id: String): Conversation? {
        val file = fileOf(id)
        if (!file.isFile)
            return null
        return runCatching { AgentJson.decodeFromString<Conversation>(AtomicFile(file).readFully().decodeToString()) }.getOrNull()
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
            preview = preview.replace('\n', ' ').take(PREVIEW_LENGTH),
            provider = conversation.provider,
            model = conversation.model,
        )
    }

    private companion object {
        const val PREVIEW_LENGTH = 140
        val ID_REGEX = Regex("^[A-Za-z0-9_-]+$")
    }
}
