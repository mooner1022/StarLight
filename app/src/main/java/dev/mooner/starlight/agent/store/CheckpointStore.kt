package dev.mooner.starlight.agent.store

import dev.mooner.starlight.agent.model.AgentJson
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.io.OutputStream

/**
 * What the agent's tools changed, kept per user message so the user can put things back as they were before it. The
 * first time a turn changes a file, a project or a project's settings, its state before is saved; later changes in
 * the same turn add nothing. Checkpoints live in the conversation's attachments folder, so they are deleted, exported
 * and imported with it.
 *
 * Only what the agent's tools change is covered: files a bot writes while it runs, or that the user edits, are not.
 */
class CheckpointStore(private val root: File) {

    @Serializable
    enum class Kind {
        @SerialName("file") FILE,
        /** A project as a whole: it didn't exist (it was created), or it was deleted. */
        @SerialName("project") PROJECT,
        /** A project's events and switch. */
        @SerialName("info") INFO,
    }

    @Serializable
    data class SavedInfo(val allowedEventIds: List<String>, val isEnabled: Boolean)

    @Serializable
    data class Entry(
        val kind: Kind,
        val project: String,
        val path: String? = null,
        /** The copy in the checkpoint's folder: the file, or the project as a zip. Null when it didn't exist. */
        val blob: String? = null,
        /** It was too large to keep, so rewinding leaves it as it is. */
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val skipped: Boolean = false,
        val info: SavedInfo? = null,
    ) {
        val key: String
            get() = key(kind, project, path)
    }

    @Serializable
    private data class Manifest(val entries: List<Entry> = emptyList())

    /** Something to put back: [entry], whose copy, if any, is in [dir]. */
    class Change(val dir: File, val entry: Entry) {
        val blobFile: File?
            get() = entry.blob?.let { File(dir, it) }
    }

    fun recorder(conversationId: String, messageId: String): Recorder =
        Recorder(dir(conversationId, messageId))

    /**
     * What rewinding to before the first of [messageIds], the user's messages from there on in order, puts back: for
     * each thing changed, its state before the earliest of them changed it.
     */
    fun changes(conversationId: String, messageIds: List<String>): List<Change> {
        val seen = HashSet<String>()
        val changes = mutableListOf<Change>()
        for (id in messageIds) {
            val dir = dir(conversationId, id)
            for (entry in read(dir).entries)
                if (seen.add(entry.key))
                    changes += Change(dir, entry)
        }
        return changes
    }

    /**
     * Forgets the changes of [messageIds], once they are put back or are to stay. The ones in [keep], by
     * [Entry.key], stay: what a rewind couldn't put back can be tried again.
     */
    fun discard(conversationId: String, messageIds: List<String>, keep: Set<String> = emptySet()) {
        for (id in messageIds) {
            val dir = dir(conversationId, id)
            val kept = if (keep.isEmpty()) emptyList() else read(dir).entries.filter { it.key in keep }
            if (kept.isEmpty()) {
                dir.deleteRecursively()
                continue
            }
            val blobs = kept.mapNotNullTo(hashSetOf()) { it.blob }
            dir.listFiles()?.filter { it.name != MANIFEST && it.name !in blobs }?.forEach { it.deleteRecursively() }
            write(dir, Manifest(kept))
        }
    }

    /**
     * Records how things are just before a rewind changes them, so the rewind can be undone; see
     * [Rewind.keepPresent]. One is kept per conversation: a new rewind takes the place of the last one's.
     */
    fun undoRecorder(conversationId: String): Recorder {
        discardUndo(conversationId)
        return Recorder(dir(conversationId, UNDO))
    }

    /** What undoing the last rewind puts back; nothing when there is none to undo. */
    fun undoChanges(conversationId: String): List<Change> {
        val dir = dir(conversationId, UNDO)
        return read(dir).entries.map { Change(dir, it) }
    }

    fun discardUndo(conversationId: String) {
        dir(conversationId, UNDO).deleteRecursively()
    }

    private fun dir(conversationId: String, messageId: String): File =
        File(File(File(root, conversationId), DIR), messageId)

    /** Saves the state of things before the agent changes them during one user message's turn. */
    class Recorder internal constructor(private val dir: File) {

        /** [path], relative to the project [directory] of [project], before a tool writes, moves or deletes it. */
        fun file(project: String, directory: File, path: String) =
            record(key(Kind.FILE, project, path)) { blob ->
                val file = File(directory, path)
                when {
                    !file.isFile                    -> Entry(Kind.FILE, project, path)
                    file.length() > MAX_FILE_BYTES  -> Entry(Kind.FILE, project, path, skipped = true)
                    else                            -> {
                        file.copyTo(File(dir, blob), overwrite = true)
                        Entry(Kind.FILE, project, path, blob = blob)
                    }
                }
            }

        /**
         * The project [name] before it is created, when [archive] is null, or before it is deleted, when [archive]
         * writes it as a zip of [bytes] in all.
         */
        fun project(name: String, bytes: Long = 0, archive: ((OutputStream) -> Unit)? = null) =
            record(key(Kind.PROJECT, name, null)) { blob ->
                when {
                    archive == null             -> Entry(Kind.PROJECT, name)
                    bytes > MAX_PROJECT_BYTES   -> Entry(Kind.PROJECT, name, skipped = true)
                    else                        -> {
                        File(dir, blob).outputStream().use(archive)
                        Entry(Kind.PROJECT, name, blob = blob)
                    }
                }
            }

        /** The events and switch of the project [name] before a tool changes them. */
        fun info(name: String, info: SavedInfo) =
            record(key(Kind.INFO, name, null)) { Entry(Kind.INFO, name, info = info) }

        @Synchronized
        private fun record(key: String, make: (blob: String) -> Entry) {
            val manifest = read(dir)
            if (manifest.entries.any { it.key == key })
                return
            dir.mkdirs()
            val entry = make("b${manifest.entries.size}")
            write(dir, Manifest(manifest.entries + entry))
        }
    }

    companion object {
        const val DIR = "checkpoints"
        /** The folder of the rewind that can be undone, beside the ones named after messages, whose ids start with "m". */
        private const val UNDO = "_undo"
        private const val MANIFEST = "manifest.json"
        /** Scripts and their data; a larger file is likely a download the bot keeps, not something to rewind. */
        const val MAX_FILE_BYTES = 5L * 1024 * 1024
        const val MAX_PROJECT_BYTES = 30L * 1024 * 1024

        private fun key(kind: Kind, project: String, path: String?): String =
            "${kind.name}:$project:${path.orEmpty()}"

        private fun read(dir: File): Manifest {
            val file = File(dir, MANIFEST)
            if (!file.isFile)
                return Manifest()
            return runCatching { AgentJson.decodeFromString(Manifest.serializer(), file.readText()) }.getOrDefault(Manifest())
        }

        private fun write(dir: File, manifest: Manifest) {
            val temp = File(dir, "$MANIFEST.new")
            temp.writeText(AgentJson.encodeToString(Manifest.serializer(), manifest))
            val target = File(dir, MANIFEST)
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }
    }
}
