package dev.mooner.starlight.agent.store

import dev.mooner.starlight.agent.store.CheckpointStore.Kind
import java.io.File

/** Puts back what [CheckpointStore.changes] lists; [Projects] does what involves StarLight's project list. */
object Rewind {

    interface Projects {
        /** The folder of the project [name], or null if there is no such project. */
        fun directory(name: String): File?
        fun remove(name: String)
        /** Loads the project saved in the zip [archive]. */
        fun restore(archive: File)
        fun setEvents(name: String, allowedEventIds: List<String>)
    }

    class Result(
        /** What was put back, such as "hello-bot/main.js". */
        val restored: List<String>,
        /** What couldn't be, with why. */
        val failed: List<String>,
        /** Projects whose files or settings were put back, to compile again. */
        val touched: Set<String>,
        /** Each project's switch as it was, to set once it is compiled again. */
        val enabled: Map<String, Boolean>,
    )

    fun apply(changes: List<CheckpointStore.Change>, projects: Projects): Result {
        val restored = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val touched = linkedSetOf<String>()
        val enabled = linkedMapOf<String, Boolean>()
        /** Projects that didn't exist before; their files go with them. */
        val removed = hashSetOf<String>()

        // Projects first, since the files below may be put back into one.
        for (change in changes.filter { it.entry.kind == Kind.PROJECT }) {
            val name = change.entry.project
            val archive = change.blobFile
            runCatching {
                check(!change.entry.skipped) { "it was too large to keep a copy" }
                if (projects.directory(name) != null)
                    projects.remove(name)
                if (archive == null) {
                    removed += name
                } else {
                    projects.restore(archive)
                    touched += name
                }
                restored += describe(change.entry)
            }.onFailure { failed += "${describe(change.entry)}: ${it.message}" }
        }
        for (change in changes.filter { it.entry.kind == Kind.FILE }) {
            val entry = change.entry
            if (entry.project in removed)
                continue
            runCatching {
                check(!entry.skipped) { "it was too large to keep a copy" }
                val dir = projects.directory(entry.project) ?: error("the project isn't there")
                val target = File(dir, entry.path ?: error("no path")).canonicalFile
                require(target.path.startsWith(dir.canonicalPath + File.separator)) { "it is outside the project" }
                val copy = change.blobFile
                if (copy != null) {
                    target.parentFile?.mkdirs()
                    copy.copyTo(target, overwrite = true)
                } else if (target.exists() && !target.delete()) {
                    error("it couldn't be deleted")
                }
                touched += entry.project
                restored += describe(entry)
            }.onFailure { failed += "${describe(entry)}: ${it.message}" }
        }
        for (change in changes.filter { it.entry.kind == Kind.INFO }) {
            val entry = change.entry
            val info = entry.info ?: continue
            if (entry.project in removed || projects.directory(entry.project) == null)
                continue
            runCatching {
                projects.setEvents(entry.project, info.allowedEventIds)
                enabled[entry.project] = info.isEnabled
                touched += entry.project
                restored += describe(entry)
            }.onFailure { failed += "${describe(entry)}: ${it.message}" }
        }
        return Result(restored, failed, touched, enabled)
    }

    /** One line for a change, for the user and for the agent. */
    fun describe(entry: CheckpointStore.Entry): String = when (entry.kind) {
        Kind.FILE    -> "${entry.project}/${entry.path}"
        Kind.PROJECT -> "${entry.project} (project)"
        Kind.INFO    -> "${entry.project} (events and switch)"
    }
}
