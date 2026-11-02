package dev.mooner.starlight.agent.store

import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.model.Conversation
import dev.mooner.starlight.agent.model.RunState
import dev.mooner.starlight.utils.unzip
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Conversations in and out as one zip, to move them to another StarLight build or phone: the conversations and the
 * files attached to them or made in them. API keys and settings stay out.
 */
internal object ConversationArchive {

    class ImportResult(val imported: Int, val skipped: Int)

    /** Writes the conversations in [root], with their attachments, to [output]; returns how many conversations. */
    fun export(root: File, output: OutputStream): Int {
        var count = 0
        ZipOutputStream(output.buffered()).use { zip ->
            for (name in listOf(AgentStorage.CONVERSATIONS, AgentStorage.ATTACHMENTS)) {
                File(root, name).walkTopDown()
                    .filter { it.isFile }
                    // Only finished files: an AtomicFile leaves a .bak or .new beside one it is writing.
                    .filter { name != AgentStorage.CONVERSATIONS || it.extension == "json" }
                    .forEach { file ->
                        zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        if (name == AgentStorage.CONVERSATIONS)
                            count++
                    }
            }
        }
        return count
    }

    /**
     * Adds the conversations of a zip made by [export] with [add], and their attachments under [attachmentsDir],
     * unpacking in [staging] first. Conversations [exists] knows are skipped, as are ones that can't be read; a run
     * that was going on when it was exported comes back as interrupted.
     */
    fun import(
        input: InputStream,
        staging: File,
        attachmentsDir: File,
        exists: (id: String) -> Boolean,
        add: (Conversation) -> Unit,
    ): ImportResult {
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            unzip(input, staging)
            val files = File(staging, AgentStorage.CONVERSATIONS).listFiles { file -> file.extension == "json" }.orEmpty()
            require(files.isNotEmpty()) { "No conversations in this file" }
            var imported = 0
            var skipped = 0
            for (file in files.sortedBy { it.name }) {
                val added = runCatching {
                    val conversation = AgentJson.decodeFromString<Conversation>(file.readText())
                    if (exists(conversation.id))
                        return@runCatching false
                    val attachments = File(staging, "${AgentStorage.ATTACHMENTS}/${conversation.id}")
                    val target = File(attachmentsDir, conversation.id)
                    if (attachments.isDirectory && !target.exists())
                        attachments.copyRecursively(target)
                    add(if (conversation.state.isActive) conversation.copy(state = RunState.INTERRUPTED) else conversation)
                    true
                }.getOrDefault(false)
                if (added) imported++ else skipped++
            }
            return ImportResult(imported, skipped)
        } finally {
            staging.deleteRecursively()
        }
    }
}
