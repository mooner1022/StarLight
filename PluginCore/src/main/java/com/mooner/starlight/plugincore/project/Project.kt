package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Session.Companion.json
import com.mooner.starlight.plugincore.language.ILanguage
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.logger.LocalLogger
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.methods.MethodManager
import com.mooner.starlight.plugincore.models.ChatRoom
import com.mooner.starlight.plugincore.models.Message
import com.mooner.starlight.plugincore.utils.Utils.Companion.hasFile
import kotlinx.serialization.encodeToString
import java.io.File

class Project(
    val directory: File,
    val config: ProjectConfig
) {
    val isCompiled: Boolean
        get() = engine != null
    private var engine: Any? = null
    private val lang: Language = Session.getLanguageManager().getLanguage(config.language)?: throw IllegalArgumentException("Cannot find language ${config.language}")
    private val logger: LocalLogger = if (directory.hasFile("logs_local.json")) {
        LocalLogger.fromFile(File(directory, "logs_local.json"))
    } else {
        LocalLogger.create(directory)
    }
    private var lastRoom: ChatRoom? = null

    private val tag: String
        get() = "Project-${config.name}"

    companion object {
        fun create(dir: File, config: ProjectConfig): Project {
            val folder = File(dir.path, config.name)
            folder.mkdirs()
            File(folder.path, "project.json").writeText(json.encodeToString(config), Charsets.UTF_8)
            val language = Session.getLanguageManager().getLanguage(config.language)?: throw IllegalArgumentException("Cannot find language ${config.language}")
            File(folder.path, config.mainScript).writeText(language.defaultCode, Charsets.UTF_8)
            return Project(folder, config)
        }
    }

    init {
        val langConfFile = File(directory, "config-language.json")
        lang.setConfigPath(langConfFile)
    }

    fun callEvent(name: String, args: Array<Any>) {
        Logger.d(tag, "calling $name with args [${args.joinToString(", ")}]")

        if (!isCompiled) {
            logger.e("EventHandler", "Property engine must not be null")
            return
        }
        if (name == "response") {
            lastRoom = (args[0] as Message).room
        }

        try {
            lang.callFunction(engine!!, name, args)
        } catch (e: Exception) {
            logger.e(config.name, "Error while running [${config.name}]: $e")
            e.printStackTrace()
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

    fun compile(throwException: Boolean = false) {
        try {
            val rawCode: String = (directory.listFiles()?.find { it.isFile && it.name == config.mainScript }?: throw IllegalArgumentException(
                    "Cannot find main script ${config.mainScript} for project ${config.name}"
            )).readText(Charsets.UTF_8)
            if (engine != null && lang.requireRelease) {
                lang.release(engine!!)
                println("engine released")
            }
            Logger.d(tag, "compile() called, methods= ${MethodManager.getMethods().joinToString { it.className }}")
            engine = lang.compile(
                rawCode,
                MethodManager.getMethods()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            logger.e("${config.name}: compile", e.toString())
            if (throwException) throw e
        }
    }

    fun flush() {
        val str = json.encodeToString(config)
        logger.d(config.name, "Flushed project config: $str")
        File(directory.path, "project.json").writeText(str, Charsets.UTF_8)
    }

    fun getLanguage(): ILanguage {
        return lang
    }
}