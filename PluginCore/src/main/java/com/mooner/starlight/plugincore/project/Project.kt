
package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.api.ApiManager
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Session.json
import com.mooner.starlight.plugincore.language.ILanguage
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.logger.LocalLogger
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.utils.Utils.Companion.hasFile
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.*
import kotlin.coroutines.CoroutineContext

class Project (
    val directory: File,
    val info: ProjectInfo
) {

    companion object {
        private const val CONFIG_FILE_NAME  = "config.json"
        private const val INFO_FILE_NAME    = "project.json"
        private const val LOGS_FILE_NAME    = "logs-local.json"

        fun create(dir: File, config: ProjectInfo): Project {
            val folder = File(dir.path, config.name)
            folder.mkdirs()
            File(folder.path, INFO_FILE_NAME).writeText(json.encodeToString(config), Charsets.UTF_8)
            val language = Session.languageManager.getLanguage(config.languageId)?: throw IllegalArgumentException("Cannot find language ${config.languageId}")
            File(folder.path, config.mainScript).writeText(language.defaultCode, Charsets.UTF_8)
            return Project(folder, config)
        }
    }

    val configManager: ConfigManager = ConfigManager(File(directory, CONFIG_FILE_NAME))

    var threadName: String? = null
    //private lateinit var scope: CoroutineScope
    private var context: CoroutineContext? = null
    val isCompiled: Boolean
        get() = engine != null
    private var engine: Any? = null
    private val lang: ILanguage = Session.languageManager.getLanguage(info.languageId)?: throw IllegalArgumentException("Cannot find language ${info.languageId}")

    val logger: LocalLogger = if (directory.hasFile(LOGS_FILE_NAME)) {
        LocalLogger.fromFile(File(directory, LOGS_FILE_NAME))
    } else {
        LocalLogger.create(directory)
    }

    private val tag: String
        get() = "Project-${info.name}"

    init {
        val confFile = File(directory, CONFIG_FILE_NAME)
        (lang as Language).setConfigFile(confFile)
    }

    fun callEvent(name: String, args: Array<Any>) {
        //logger.d(tag, "calling $name with args [${args.joinToString(", ")}]")
        if (engine == null) {
            if (!isCompiled) return

            val thread = Thread.currentThread()
            logger.w("EventHandler", """
                Property engine must not be null
                이 에러는 StarLight 의 버그일 수 있습니다.
                [버그 제보시 아래 메세지를 첨부해주세요.]
                ──────────
                thread    : ${thread.name}
                eventName : $name
                args      : $args
                ┉┉┉┉┉┉┉┉┉┉
                language  : ${info.languageId}
                listeners : ${info.listeners}
                plugins   : ${info.pluginIds}
                packages  : ${info.packages}
                ──────────
            """.trimIndent())
            return
        }

        if (threadName == null || context == null) {
            if (context != null) {
                context?.cancel()
                context = null

            }
            threadName = "$tag-worker"
            context = newFixedThreadPoolContext(3, threadName!!)
            Logger.v("Allocated thread $threadName with 3 threads to project ${info.name}")
        }

        fun onError(e: Throwable) {
            logger.e(tag, "Error while running: $e")
            val config = configManager.getConfigForId("general")
            val key = "shutdown_on_error"
            val shutdownOnError: Boolean = when {
                config == null -> true
                !config.containsKey(key) -> true
                else -> config[key]!!.cast() as Boolean
            }

            if (shutdownOnError) {
                logger.e(info.name, "Shutting down project [${info.name}]...")
                info.isEnabled = false
                if (engine != null && lang.requireRelease) {
                    lang.release(engine!!)
                    engine = null
                }
            }
            e.printStackTrace()
        }

        val jobName: String = UUID.randomUUID().toString()
        val job = CoroutineScope(context!!).launch {
            lang.callFunction(engine!!, name, args) {
                onError(it)
            }
        }
        JobLocker.withParent(threadName!!).registerJob(
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
                    onError(e)
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
            val rawCode: String = (directory.listFiles()?.find { it.isFile && it.name == info.mainScript }?: throw IllegalArgumentException(
                    "Cannot find main script ${info.mainScript} for project ${info.name}"
            )).readText(Charsets.UTF_8)
            if (engine != null && lang.requireRelease) {
                lang.release(engine!!)
                logger.v(tag, "engine released")
            }
            logger.v(tag, "compile() called, methods= ${ApiManager.getApis().joinToString { it.name }}")
            logger.i("Compiling project ${info.name}...")
            engine = lang.compile(
                code = rawCode,
                apis = ApiManager.getApis(),
                project = this
            )
        } catch (e: Exception) {
            e.printStackTrace()
            logger.e(tag, e.toString())
            if (throwException) throw e
        }
    }

    fun saveConfig() {
        CoroutineScope(Dispatchers.IO).launch {
            val str = synchronized(info) { json.encodeToString(info) }
            File(directory.path, INFO_FILE_NAME).writeText(str, Charsets.UTF_8)
        }
    }

    fun getLanguage(): ILanguage {
        return lang
    }

    fun activeJobs(): Int {
        return if (context == null || threadName == null) 0
        else JobLocker.withParent(threadName!!).activeJobs()
    }

    fun destroy() {
        if (context != null) {
            if (threadName != null) {
                JobLocker.withParent(threadName!!).purge()
            }
            (context as CoroutineDispatcher).cancel()
            context = null
        }
    }
}