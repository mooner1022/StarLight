package dev.mooner.starlight.ui.projects

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Moves projects in and out of StarLight as zip files through the system file picker. Google Play
 * builds keep projects in the app-specific folder, which other apps can't open, so this is how users
 * back them up or bring them in.
 */
internal object ProjectArchive {

    private const val INFO_FILE_NAME = "project.json"
    private val SKIPPED_FILES = setOf("logs.json")

    /** Writes the project's folder, without its logs, to [output] as a zip. */
    fun export(project: Project, output: OutputStream) {
        val root = project.directory
        ZipOutputStream(output.buffered()).use { zip ->
            root.walkTopDown()
                .filter { it.isFile && it.relativeTo(root).invariantSeparatorsPath !in SKIPPED_FILES }
                .forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    /**
     * Unpacks a zip made by [export], with its files at the root or in one folder, as a new project
     * and loads it. Refuses a project whose name or id is already taken.
     */
    fun import(input: InputStream): Project {
        val baseDir = getStarLightDirectory()
        val staging = baseDir.resolve(".project-import").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            unzip(input, staging)
            val root = staging.takeIf { it.resolve(INFO_FILE_NAME).isFile }
                ?: staging.listFiles()?.singleOrNull { it.isDirectory && it.resolve(INFO_FILE_NAME).isFile }
                ?: throw IllegalArgumentException("$INFO_FILE_NAME not found")

            val info = Session.projectLoader.readProjectInfo(root)
            val name = info.name
            require(name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' }) {
                "Invalid project name: $name"
            }
            require(projectManager.getProjectByName(name) == null) { "A project named '$name' already exists" }
            require(projectManager.getProjectById(info.id.toString()) == null) { "This project is already here" }

            val target = baseDir.resolve("projects").resolve(name)
            require(!target.exists()) { "A folder named '$name' already exists" }
            if (!root.renameTo(target)) {
                root.copyRecursively(target)
            }
            return Session.projectLoader.loadProject(target)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun unzip(input: InputStream, dir: File) {
        val rootPath = dir.canonicalPath + File.separator
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = dir.resolve(entry.name).canonicalFile
                require(target.path.startsWith(rootPath)) { "Invalid entry: ${entry.name}" }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }
}
