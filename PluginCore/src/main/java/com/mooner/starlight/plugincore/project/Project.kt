
package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.Session.apiManager
import com.mooner.starlight.plugincore.Session.json
import com.mooner.starlight.plugincore.Session.projectManager
import com.mooner.starlight.plugincore.config.Config
import com.mooner.starlight.plugincore.config.FileConfig
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.logger.LocalLogger
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.utils.hasFile
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

        private const val DEF_THREAD_POOL_SIZE = 3

        fun create(dir: File, config: ProjectInfo): Project {
            val folder = File(dir.path, config.name)
            folder.mkdirs()
            File(folder.path, INFO_FILE_NAME).writeText(json.encodeToString(config), Charsets.UTF_8)
            val language = Session.languageManager.getLanguage(config.languageId)?: throw IllegalArgumentException("Cannot find language ${config.languageId}")
            File(folder.path, config.mainScript).writeText(language.defaultCode, Charsets.UTF_8)
            return Project(folder, config)
        }
    }

    //val configManager: ConfigManager = ConfigManager(File(directory, CONFIG_FILE_NAME))
    val config: Config = FileConfig(File(directory, CONFIG_FILE_NAME))

    var threadName: String? = null
    //private lateinit var scope: CoroutineScope
    private var context: CoroutineContext? = null
    val isCompiled: Boolean
        get() = engine != null
    private var engine: Any? = null
    private val lang: Language = Session.languageManager.getLanguage(info.languageId)?: throw IllegalArgumentException("Cannot find language ${info.languageId}")

    val logger: LocalLogger = if (directory.hasFile(LOGS_FILE_NAME)) {
        LocalLogger.fromFile(File(directory, LOGS_FILE_NAME))
    } else {
        LocalLogger.create(directory)
    }

    private val tag: String
        get() = "Project-${info.name}"

    init {
        val confFile = File(directory, CONFIG_FILE_NAME)
        lang.setConfigFile(confFile)
    }

    /**
     * Calls an event with [name] and [args] as parameter.
     *
     * @param name the name of function or event being called.
     * @param args parameter values being passed to function.
     * @param onException called when exception is occurred while calling.
     */
    fun callEvent(name: String, args: Array<out Any>, onException: (Throwable) -> Unit = {}) {
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
            val poolSize = getThreadPoolSize()
            context = newFixedThreadPoolContext(poolSize, threadName!!)
            Logger.v("Allocated thread pool $threadName with $poolSize threads to project ${info.name}")
        }

        fun onError(e: Throwable) {
            logger.e(tag, "Error while running: $e")
            val key = "shutdown_on_error"
            val shutdownOnError: Boolean = config["general"].getBoolean(key, true)

            if (shutdownOnError) {
                logger.e(info.name, "Shutting down project [${info.name}]...")
                info.isEnabled = false
                if (engine != null && lang.requireRelease) {
                    lang.release(engine!!)
                    engine = null
                }
            }
            e.printStackTrace()
            projectManager.onStateChanged(this)
            onException(e)
        }

        val jobName: String = UUID.randomUUID().toString()
        val job = CoroutineScope(context!!).launch {
            lang.callFunction(engine!!, name, args, ::onError)
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

    /**
     * Compiles the project with configured values.
     *
     * @param throwException if *true*, throws exceptions occurred during compilation.
     */
    fun compile(throwException: Boolean = false): Boolean {
        try {
            val rawCode: String = (directory.listFiles()?.find { it.isFile && it.name == info.mainScript }?: throw IllegalArgumentException(
                    "Cannot find main script ${info.mainScript} for project ${info.name}"
            )).readText(Charsets.UTF_8)
            if (engine != null && lang.requireRelease) {
                lang.release(engine!!)
                logger.v(tag, "engine released")
            }
            logger.i("Compiling project ${info.name}...")
            engine = lang.compile(
                code = rawCode,
                apis = apiManager.getApis(),
                project = this
            )
            projectManager.onStateChanged(this)
        } catch (e: Exception) {
            e.printStackTrace()
            logger.e(tag, e.toString())
            if (throwException) throw e
            return false
        }
        return true
    }

    fun setEnabled(enabled: Boolean): Boolean {
        if (isCompiled) {
            if (info.isEnabled) return true
            info.isEnabled = true
            saveInfo()
            requestUpdate()
            return true
        }
        return false
    }

    /**
     * Requests the application to update UI of the project.
     *
     * Ignored if application is on background.
     */
    fun requestUpdate() {
        projectManager.onStateChanged(this)
    }

    /**
     * Saves contents of [info] to a file.
     */
    fun saveInfo() {
        CoroutineScope(Dispatchers.IO).launch {
            val str = synchronized(info) { json.encodeToString(info) }
            File(directory.path, INFO_FILE_NAME).writeText(str, Charsets.UTF_8)
        }
    }

    /**
     * Returns the language used in the project.
     *
     * @return language used in the project.
     */
    fun getLanguage(): Language {
        return lang
    }

    /**
     * Count of jobs currently running in thread pool of this project.
     *
     * @return the size of running jobs.
     */
    fun activeJobs(): Int {
        return if (context == null || threadName == null) 0
        else JobLocker.withParent(threadName!!).activeJobs()
    }

    fun stopAllJobs() {
        if (context != null) {
            if (threadName != null) {
                JobLocker.withParent(threadName!!).purge()
            }
            (context as CoroutineDispatcher).cancel()
            context = null
        }
    }

    /**
     * Cancels all running jobs and releases [engine] if the project is compiled.
     *
     * @param requestUpdate if *true*, requests update of UI
     */
    fun destroy(requestUpdate: Boolean = false) {
        stopAllJobs()
        if (engine != null) {
            if (lang.requireRelease)
                lang.release(engine!!)
            engine = null
        }
        if (requestUpdate) requestUpdate()
    }

    private fun getThreadPoolSize(): Int = config.getCategory("beta_features").getInt("thread_pool_size", DEF_THREAD_POOL_SIZE)
}