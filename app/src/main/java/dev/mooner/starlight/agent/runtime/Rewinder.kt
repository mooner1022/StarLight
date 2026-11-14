package dev.mooner.starlight.agent.runtime

import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.store.CheckpointStore
import dev.mooner.starlight.agent.store.Rewind
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.ui.projects.ProjectArchive
import dev.mooner.starlight.utils.zip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Puts StarLight's projects back as they were before the agent's turns, from the checkpoints kept for them. */
internal class Rewinder(private val hub: AgentHub) {

    private val projects = object : Rewind.Projects {
        override fun directory(name: String): File? =
            Session.projectManager.getProjectByName(name)?.directory

        override fun remove(name: String) =
            Session.projectManager.removeProject(name, removeFiles = true)

        override fun restore(archive: File) {
            // Put back as it was, on if it was on: this undoes a removal, it doesn't bring in something new.
            archive.inputStream().use { ProjectArchive.import(it, keepEnabled = true) }
        }

        override fun setEvents(name: String, allowedEventIds: List<String>) =
            Session.projectManager.updateProjectInfo(name, true) {
                this.allowedEventIds.clear()
                this.allowedEventIds.addAll(allowedEventIds)
            }

        override fun info(name: String): CheckpointStore.SavedInfo? =
            Session.projectManager.getProjectByName(name)?.info
                ?.let { CheckpointStore.SavedInfo(it.allowedEventIds.toList(), it.isEnabled) }
    }

    /**
     * Puts back what the turns of [messageIds], the user's messages from one on, changed, then forgets their
     * checkpoints. A project put back is compiled again if it runs, so that its old code is what runs. How things
     * were just before is kept, for [undo].
     */
    suspend fun rewind(conversationId: String, messageIds: List<String>): Rewind.Result = withContext(Dispatchers.IO) {
        val changes = hub.checkpoints.changes(conversationId, messageIds)
        // Before anything is written over: the files as they are now may hold the user's own edits.
        runCatching { Rewind.keepPresent(changes, projects, hub.checkpoints.undoRecorder(conversationId), ::zip) }
            .onFailure { hub.checkpoints.discardUndo(conversationId) }
        val result = settle(Rewind.apply(changes, projects))
        // What couldn't be put back stays, to be tried again.
        hub.checkpoints.discard(conversationId, messageIds, keep = result.failedKeys)
        result
    }

    /** Whether the last rewind of the conversation can be undone. */
    fun canUndo(conversationId: String): Boolean =
        hub.checkpoints.undoChanges(conversationId).isNotEmpty()

    /** Puts things back as they were just before the last rewind. It can be done once. */
    suspend fun undo(conversationId: String): Rewind.Result = withContext(Dispatchers.IO) {
        val result = settle(Rewind.apply(hub.checkpoints.undoChanges(conversationId), projects))
        hub.checkpoints.discardUndo(conversationId)
        result
    }

    /** Compiles the projects [result] touched again if they run, and sets their switches as they were. */
    private fun settle(result: Rewind.Result): Rewind.Result {
        val failed = result.failed.toMutableList()
        for (name in result.touched) {
            val project = Session.projectManager.getProjectByName(name) ?: continue
            val on = result.enabled[name] ?: project.info.isEnabled
            if (project.isCompiled || on)
                runCatching { project.compile(true) }.onFailure { failed += "$name: it didn't compile (${it.message})" }
            if (project.info.isEnabled != on)
                project.setEnabled(on)
        }
        return Rewind.Result(result.restored, failed, result.touched, result.enabled, result.failedKeys)
    }
}
