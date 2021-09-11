package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Session.Companion.json
import com.mooner.starlight.plugincore.language.ILanguage
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.logger.LocalLogger
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.method.MethodManager
import com.mooner.starlight.plugincore.utils.Utils.Companion.hasFile
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.*

class Project(
    val directory: File,
    val config: ProjectConfig
) {

    companion object {
        private const val LANGUAGE_CONFIG_FILE_NAME  = "config-language.json"
        private const val PROJECT_CONFIG_FILE_NAME   = "project.json"
        private const val LOGS_FILE_NAME             = "logs_local.json"

        fun create(dir: File, config: ProjectConfig): Project {
            val folder = File(dir.path, config.name)
            folder.mkdirs()
            File(folder.path, PROJECT_CONFIG_FILE_NAME).writeText(json.encodeToString(config), Charsets.UTF_8)
            val language = Session.getLanguageManager().getLanguage(config.languageId)?: throw IllegalArgumentException("Cannot find language ${config.languageId}")
            File(folder.path, config.mainScript).writeText(language.defaultCode, Charsets.UTF_8)
            return Project(folder, config)
        }
    }

    private val scope: CoroutineScope = CoroutineScope(Job() + Dispatchers.Default)
    val isCompiled: Boolean
        get() = engine != null
    private var engine: Any? = null
    private val lang: Language = Session.getLanguageManager().getLanguage(config.languageId)?: throw IllegalArgumentException("Cannot find language ${config.languageId}")

    val logger: LocalLogger = if (directory.hasFile(LOGS_FILE_NAME)) {
        LocalLogger.fromFile(File(directory, LOGS_FILE_NAME))
    } else {
        LocalLogger.create(directory)
    }

    private val tag: String
        get() = "Project-${config.name}"

    init {
        val langConfFile = File(directory, LANGUAGE_CONFIG_FILE_NAME)
        lang.setConfigPath(langConfFile)
    }

    fun callEvent(name: String, args: Array<Any>) {
        //logger.d(tag, "calling $name with args [${args.joinToString(", ")}]")

        if (!isCompiled) {
            logger.w("EventHandler", """
                Property engine must not be null
                This might be a bug of StarLight
            """.trimIndent())
            return
        }

        val jobName = "$tag-worker-${UUID.randomUUID()}"
        val job = scope.launch(newSingleThreadContext(jobName)) {
            lang.callFunction(engine!!, name, args)
        }
        JobLocker.registerJob(
            key = jobName,
            job = job
        ) { e ->
            when(e) {
                is ForceReleasedException, is CancellationException -> {
                    Logger.w(tag, "Task $jobName canceled")
                    if (engine != null)
                        lang.release(engine!!)
                }
                null -> {
                    if (engine != null)
                        lang.release(engine!!)
                }
                else -> {
                    logger.e(tag, "Error while running: $e")
                    val shutdownOnError: Boolean?
                    if ((lang.getLanguageConfig()["shutdown_on_error"] as Boolean?).also { shutdownOnError = it } != null) {
                        if (shutdownOnError!!) {
                            logger.e(config.name, "Shutting down project [${config.name}]...")
                            config.isEnabled = false
                            if (engine != null && lang.requireRelease) {
                                lang.release(engine!!)
                                engine = null
                            }
                        }
                    }
                    e.printStackTrace()
                }
            }
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
                logger.d(tag, "engine released")
            }
            logger.d(tag, "compile() called, methods= ${MethodManager.getMethods().joinToString { it.name }}")
            engine = lang.compile(
                code = rawCode,
                methods = MethodManager.getMethods(),
                project = this
            )
        } catch (e: Exception) {
            e.printStackTrace()
            logger.e(tag, e.toString())
            if (throwException) throw e
        }
    }

    fun saveConfig() {
        val str = json.encodeToString(config)
        logger.d(config.name, "Flushed project config: $str")
        File(directory.path, PROJECT_CONFIG_FILE_NAME).writeText(str, Charsets.UTF_8)
    }

    fun getLanguage(): ILanguage {
        return lang
    }
}