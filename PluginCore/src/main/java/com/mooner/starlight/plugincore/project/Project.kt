package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.language.Languages
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.io.File

class Project(
        folder: File,
        val config: ProjectConfig
) {
    private val interpreter: Any
    val directory: File

    companion object {
        fun create(dir: File, config: ProjectConfig): Project {
            val folder = File(dir.path, config.name)
            folder.mkdirs()
            File(folder.path, "project.json").writeText(Json.encodeToString(config), Charsets.UTF_8)
            File(folder.path, config.mainScript).writeText("", Charsets.UTF_8)
            return Project(folder, config)
        }
    }

    init {
        val rawCode: String = (folder.listFiles()?.find { it.isFile && it.name == config.mainScript }?:throw IllegalArgumentException("Cannot find main script ${config.mainScript} for project ${config.name}"))
                .readText(Charsets.UTF_8)

        directory = folder

        interpreter = when(config.language) {
            Languages.JS_RHINO -> {
                val factory = ContextFactory.getGlobal()
                val context = factory.enterContext().apply {
                    optimizationLevel = -1
                    languageVersion = Context.VERSION_ES6
                }
                val shared = context.initStandardObjects()
                val scope = context.newObject(shared)
                context.evaluateString(scope, rawCode, config.mainScript, 1, null)
                scope
            }
            else -> {
                ""
            }
        }
    }

    fun run(args: Array<Any>) {
        when(config.language) {
            Languages.JS_RHINO -> {
                ScriptableObject.callMethod(interpreter as Scriptable, "response", args)
            }
        }
    }
}