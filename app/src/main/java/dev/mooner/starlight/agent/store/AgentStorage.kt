package dev.mooner.starlight.agent.store

import android.content.Context
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import java.io.File

private val LOG = LoggerFactory.logger("AgentStorage")

/**
 * Where conversations and their attachments are kept: the StarLight folder, which outlives a reinstall in builds
 * that use shared storage. The app's own folder, wiped with the app, holds them only while the StarLight folder
 * can't be written, such as before all-files access is granted; they move over once it can.
 */
internal object AgentStorage {

    const val DIR = "agent"
    const val CONVERSATIONS = "conversations"
    const val ATTACHMENTS = "attachments"
    const val INDEX = "index.json"

    fun root(context: Context): File {
        val private = File(context.filesDir, DIR)
        val shared = getStarLightDirectory().resolve(DIR)
        shared.mkdirs()
        if (!shared.isDirectory || !shared.canWrite()) {
            LOG.warn { "Can't write to ${shared.path}; conversations stay in the app's own folder for now" }
            return private
        }
        runCatching { migrate(private, shared) }
            .onSuccess { moved -> if (moved > 0) LOG.info { "Moved $moved conversations to ${shared.path}" } }
            .onFailure { LOG.error { "Couldn't move conversations to ${shared.path}: $it" } }
        return shared
    }

    /**
     * Moves the conversations and attachments in [from] into [to], keeping those [to] already has, then deletes
     * [from]; returns how many conversations moved. The index of [to] is dropped when any did, so it is rebuilt from
     * the files.
     */
    fun migrate(from: File, to: File): Int {
        val conversations = File(from, CONVERSATIONS).listFiles { file -> file.isFile }.orEmpty()
        val attachments = File(from, ATTACHMENTS).listFiles { file -> file.isDirectory }.orEmpty()
        if (conversations.isEmpty() && attachments.isEmpty()) {
            from.deleteRecursively()
            return 0
        }
        var moved = 0
        for (file in conversations)
            if (move(file, File(to, "$CONVERSATIONS/${file.name}")))
                moved++
        for (dir in attachments)
            move(dir, File(to, "$ATTACHMENTS/${dir.name}"))
        if (moved > 0)
            File(to, INDEX).delete()
        from.deleteRecursively()
        return moved
    }

    /** Moves [source] to [target] unless [target] exists; the folders may be on different storage. */
    private fun move(source: File, target: File): Boolean {
        if (target.exists())
            return false
        target.parentFile?.mkdirs()
        if (source.renameTo(target))
            return true
        source.copyRecursively(target)
        return true
    }
}
