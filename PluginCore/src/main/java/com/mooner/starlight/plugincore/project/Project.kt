package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.Session.Companion.getLogger
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.logger.LocalLogger
import com.mooner.starlight.plugincore.utils.Utils
import com.mooner.starlight.plugincore.utils.Utils.Companion.hasFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class Project(
    private val folder: File,
    val config: ProjectConfig
) {
    private var engine: Any? = null
    private val lang: Language
    private val logger: LocalLogger
    val directory: File
    private var listener: ((room: String, msg: String) -> Unit)? = null
    private var lastRoom: String? = null
    private val defReplier = object :Replier {
        override fun reply(msg: String) {
            if (lastRoom != null) {
                listener!!(lastRoom!!, msg)
            }
        }

        override fun replyTo(room: String, msg: String) {
            listener!!(room, msg)
        }
    }

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
        )).readText(Charsets.UTF_8)
        directory = folder
        logger = if (directory.hasFile("logs_local.json")) {
            LocalLogger.fromFile(File(directory, "logs_local.json"))
        } else {
            LocalLogger.create(directory)
        }

        lang = Session.getLanguageManager().getLanguage(config.language)?: throw IllegalArgumentException("Cannot find language ${config.language}")
        engine = lang.compile(
            rawCode,
            Utils.getDefaultMethods(defReplier, logger)
        )
    }

    fun callEvent(methodName: String, args: Array<Any>) {
        println("calling $methodName with args [${args.joinToString(", ")}]")

        if (engine == null) {
            logger.e("EventHandler", "Property engine must not be null")
            return
        }
        if (methodName == "response") {
            lastRoom = args[0] as String
        }

        try {
            lang.execute(engine!!, methodName, args)
        } catch (e: Exception) {
            logger.e(config.name, "Error while executing: $e")
        }
        /*
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
        */
    }

    fun recompile() {
        val rawCode: String = (folder.listFiles()?.find { it.isFile && it.name == config.mainScript }?:throw IllegalArgumentException(
            "Cannot find main script ${config.mainScript} for project ${config.name}"
        )).readText(Charsets.UTF_8)
        if (engine != null && lang.requireRelease) {
            lang.release(engine!!)
            println("engine released")
        }
        engine = lang.compile(
            rawCode,
            Utils.getDefaultMethods(defReplier, logger)
        )
    }

    fun flush() {
        File(directory.path, "project.json").writeText(Json.encodeToString(config), Charsets.UTF_8)
    }

    fun bindReplier(listener: (room: String, msg: String) -> Unit) {
        this.listener = listener
    }

    fun getLanguage(): Language {
        return lang
    }
}