package dev.mooner.starlight.agent.tools

import java.io.File
import java.security.MessageDigest

/** Files of one project, reached only through paths that stay inside its directory. */
class ProjectFiles(directory: File) {

    private val root: File = directory.canonicalFile

    /** [path] inside the project directory; anything leading outside of it is refused. */
    fun resolve(path: String): File {
        if (File(path).isAbsolute)
            throw ToolException("Path must be relative to the project directory: $path")
        val file = root.resolve(path).canonicalFile
        if (file != root && !file.path.startsWith(root.path + File.separator))
            throw ToolException("Path is outside the project directory: $path")
        return file
    }

    fun relative(file: File): String =
        file.relativeTo(root).invariantSeparatorsPath

    fun list(path: String, limit: Int): Pair<List<File>, Boolean> {
        val dir = resolve(path)
        if (!dir.isDirectory)
            throw ToolException("Not a directory: $path")
        val files = dir.walkTopDown()
            .onEnter { it == dir || it.name !in SKIPPED_DIRECTORIES }
            .filter { it != dir }
            .take(limit + 1)
            .toList()
        return files.take(limit) to (files.size > limit)
    }

    fun read(path: String): Pair<File, String> {
        val file = resolve(path)
        if (!file.isFile)
            throw ToolException("No such file: $path")
        if (file.length() > MAX_TEXT_BYTES)
            throw ToolException("$path is larger than ${MAX_TEXT_BYTES / 1024} KB")
        if (isBinary(file))
            throw ToolException("$path is a binary file")
        return file to file.readText()
    }

    /** Writes [text], checking [expectedHash] first; `""` means the file must not exist yet. */
    fun write(path: String, text: String, expectedHash: String?): File = synchronized(LOCK) {
        val file = resolve(path)
        if (file.isDirectory)
            throw ToolException("Is a directory: $path")
        checkHash(file, expectedHash)
        file.parentFile?.mkdirs()
        file.writeText(text)
        file
    }

    /** Replaces the one occurrence of [old] with [new]. */
    fun edit(path: String, old: String, new: String, expectedHash: String?): File = synchronized(LOCK) {
        if (old.isEmpty())
            throw ToolException("The text to replace is empty")
        val file = resolve(path)
        if (!file.isFile)
            throw ToolException("No such file: $path")
        checkHash(file, expectedHash)
        val text = file.readText()
        val first = text.indexOf(old)
        if (first < 0)
            throw ToolException("The text to replace was not found in $path. Read the file again; it may have changed.")
        if (text.indexOf(old, first + 1) >= 0)
            throw ToolException("The text to replace occurs more than once in $path; include more surrounding lines")
        file.writeText(text.substring(0, first) + new + text.substring(first + old.length))
        file
    }

    fun search(query: String, isRegex: Boolean, ignoreCase: Boolean, limit: Int): List<String> {
        if (query.isEmpty())
            throw ToolException("Empty search")
        val regex = try {
            Regex(if (isRegex) query else Regex.escape(query), if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        } catch (e: IllegalArgumentException) {
            throw ToolException("Invalid regular expression: ${e.message}")
        }
        val hits = mutableListOf<String>()
        for (file in root.walkTopDown().onEnter { it == root || it.name !in SKIPPED_DIRECTORIES }) {
            if (!file.isFile || file.length() > MAX_TEXT_BYTES || isBinary(file))
                continue
            file.useLines { lines ->
                for ((i, line) in lines.withIndex())
                    if (regex.containsMatchIn(line)) {
                        hits += "${relative(file)}:${i + 1}: ${line.take(MAX_HIT_LENGTH)}"
                        if (hits.size >= limit)
                            return hits
                    }
            }
        }
        return hits
    }

    private fun checkHash(file: File, expectedHash: String?) {
        if (expectedHash == null)
            return
        val current = if (file.isFile) sha256(file.readBytes()) else null
        if (current != expectedHash.ifEmpty { null })
            throw ToolException(
                if (current == null) "${relative(file)} doesn't exist"
                else "${relative(file)} changed since it was read (hash $current); read it again"
            )
    }

    companion object {
        const val MAX_TEXT_BYTES = 1024L * 1024
        private const val MAX_HIT_LENGTH = 300
        private val LOCK = Any()

        /** Folders the app keeps in projects for itself. */
        private val SKIPPED_DIRECTORIES = setOf("debugroom", ".git", "node_modules")

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun isBinary(file: File): Boolean =
            file.inputStream().use { input ->
                val buffer = ByteArray(8 * 1024)
                val read = input.read(buffer)
                (0 until maxOf(read, 0)).any { buffer[it] == 0.toByte() }
            }
    }
}
