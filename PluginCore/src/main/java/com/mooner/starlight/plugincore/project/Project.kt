package com.mooner.starlight.plugincore.project

import com.eclipsesource.v8.V8
import com.mooner.starlight.plugincore.compiler.Compiler
import com.mooner.starlight.plugincore.language.Languages
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.io.File

class Project(
    folder: File,
    val config: ProjectConfig
) {
    private val engine: Any
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
        val rawCode: String = (folder.listFiles()?.find { it.isFile && it.name == config.mainScript }?:throw IllegalArgumentException(
            "Cannot find main script ${config.mainScript} for project ${config.name}"
        ))
                .readText(Charsets.UTF_8)
        directory = folder
        engine = Compiler.compile(config.language, config.mainScript, rawCode)
    }

    fun callEvent(methodName: String, args: Array<Any>) {
        when(config.language) {
            Languages.JS_RHINO -> {
                ScriptableObject.callMethod(engine as Scriptable, methodName, args)
            }
            Languages.JS_V8 -> {
                (engine as V8).executeJSFunction(methodName, *args)
            }
            else -> {

            }
        }
    }

    fun flush() {
        File(directory.path, "project.json").writeText(Json.encodeToString(config), Charsets.UTF_8)
    }
}