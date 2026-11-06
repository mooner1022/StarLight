package dev.mooner.starlight.plugincore.editor

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.project.Project
import java.io.File
import kotlin.properties.Delegates.notNull

abstract class CodeEditorActivity: AppCompatActivity() {

    private var mBaseDirectory: File by notNull()
    private var mainFile: String by notNull()
    private var mProject: Project? = null

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val baseDirectoryPath = intent.getStringExtra(KEY_BASE_DIRECTORY)
            ?: error("Failed to retrieve base directory path")
        mBaseDirectory = File(baseDirectoryPath).also {
            require(it.isValidDirectory()) {
                "Target file '${baseDirectoryPath}' isn't a valid file" }
        }

        mainFile = intent.getStringExtra(KEY_MAIN_FILE)
            ?: error("Failed to retrieve main file")

        val projectName = intent.getStringExtra(KEY_PROJECT_NAME)

        if (!projectName.isNullOrBlank()) {
            mProject = Session.projectManager.getProjectByName(projectName)
                ?: error("Failed to find project with name '$projectName'")
        }
    }

    protected fun getProject(): Project? = mProject

    protected fun getBaseDirectory(): File = mBaseDirectory

    protected fun getMainFileName(): String = mainFile

    protected fun readCode(fileName: String): String? {
        val targetFile = mBaseDirectory.resolve(fileName).also {
            if (!it.isValidFile()) return null
        }

        return try {
            targetFile.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
    }

    protected fun readCodeOrDefault(fileName: String, language: Language) : String =
        readCode(fileName) ?: language.formatDefaultCode(fileName, emptyList())

    protected fun saveCode(fileName: String, code: String): Boolean {
        val targetFile = mBaseDirectory.resolve(fileName).also {
            if (!it.isValidFile()) return false
        }

        return try {
            targetFile.writeText(code, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun File.isValidDirectory(): Boolean =
        exists() && isDirectory

    private fun File.isValidFile(): Boolean =
        exists() && isFile && canRead() && canWrite()

    companion object {
        const val KEY_BASE_DIRECTORY = "base_directory"
        const val KEY_MAIN_FILE      = "main_file"
        const val KEY_PROJECT_NAME   = "project_name"
    }
}