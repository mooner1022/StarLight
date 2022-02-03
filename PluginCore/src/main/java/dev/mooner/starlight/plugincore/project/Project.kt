/*
 * Project.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.eventManager
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.config.data.Config
import dev.mooner.starlight.plugincore.config.data.FileConfig
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.logger.LocalLogger
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.pipeline.CompilePipeline
import dev.mooner.starlight.plugincore.pipeline.PipelineInfo
import dev.mooner.starlight.plugincore.utils.hasFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import java.io.File
import kotlin.coroutines.CoroutineContext

typealias StageChangeListener = (stage: String, percentage: Int) -> Unit

class Project (
    val directory: File,
    val info: ProjectInfo
): CoroutineScope {

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

    var threadPoolName: String? = null
    //private lateinit var scope: CoroutineScope

    override val coroutineContext: CoroutineContext by lazy {
        if (threadPoolName == null || context == null) {
            if (context != null) {
                context?.cancel()
                context = null
            }
            threadPoolName = "$tag-worker"
            val poolSize = getThreadPoolSize()
            context = newFixedThreadPoolContext(poolSize, threadPoolName!!)
            Logger.v("Allocated thread pool $threadPoolName with $poolSize threads to project ${info.name}")
        }
        SupervisorJob() + context!!
    }
    private var context: CoroutineContext? = null

    val isCompiled: Boolean
        get() = langScope != null
    private var langScope: Any? = null
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
     * Calls an event with [name] and passes [args] as parameter.
     *
     * @param name the name of function or event being called.
     * @param args parameter values being passed to function.
     * @param onException called when exception is occurred while calling.
     */
    fun callFunction(name: String, args: Array<out Any>, onException: (Throwable) -> Unit = {}) {
        //logger.d(tag, "calling $name with args [${args.joinToString(", ")}]")
        if (langScope == null) {
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
                listeners : ${info.allowedEventIds}
                packages  : ${info.packages}
                ──────────
            """.trimIndent())
            return
        }

        if (threadPoolName == null || context == null) {
            if (context != null) {
                context?.cancel()
                context = null
            }
            threadPoolName = "$tag-worker"
            val poolSize = getThreadPoolSize()
            context = newFixedThreadPoolContext(poolSize, threadPoolName!!)
            Logger.v("Allocated thread pool $threadPoolName with $poolSize threads to project ${info.name}")
        }

        fun onError(e: Throwable) {
            logger.e(tag, "Error while running: $e")
            val key = "shutdown_on_error"
            val shutdownOnError: Boolean = config["general"].getBoolean(key, true)

            if (shutdownOnError) {
                logger.e(info.name, "Shutting down project '${info.name}'...")
                info.isEnabled = false
                if (langScope != null && lang.requireRelease) {
                    lang.release(langScope!!)
                    langScope = null
                }
            }
            e.printStackTrace()
            projectManager.onStateChanged(this)
            onException(e)
        }

        CoroutineScope(context!!).launch {
            try {
                JobLocker.withLock(threadPoolName!!) {
                    lang.callFunction(langScope!!, name, args, ::onError)
                }
                if (langScope != null && lang.requireRelease)
                    lang.release(langScope!!)
            } catch (e: Error) {
                onError(e)
            }
        }

        /*
        JobLocker.withParent(threadPoolName!!).registerJob(
            key = jobName,
            job = job
        ) { e ->
            when(e) {
                is ForceReleasedException, is CancellationException -> {
                    Logger.w(tag, "Task ${currentThread.name} canceled")
                    if (langScope != null && lang.requireRelease)
                        lang.release(langScope!!)
                }
                null -> {
                    if (langScope != null && lang.requireRelease)
                        lang.release(langScope!!)
                }
                else -> {
                    onError(e)
                }
            }
        }
         */
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
    fun compile(throwException: Boolean = false, onStageChanged: StageChangeListener? = null): Boolean {
        val pipeline = CompilePipeline(
            project = this,
            info = PipelineInfo(
                stages = hashSetOf(
                    //DemoPipelineStage(),
                    //DemoPipelineStage()
                )
            )
        ) { stage, perc ->
            if (onStageChanged != null) onStageChanged(stage.name, perc)
        }
        try {
            val rawCode: String = (directory.listFiles()?.find { it.isFile && it.name == info.mainScript }?: throw IllegalArgumentException(
                    "Cannot find main script ${info.mainScript} for project ${info.name}"
            )).readText(Charsets.UTF_8)
            if (langScope != null && lang.requireRelease) {
                lang.release(langScope!!)
                logger.v(tag, "engine released")
            }
            logger.i("Compiling project ${info.name}...")
            langScope = pipeline.run(rawCode)
            /*
            langScope = lang.compile(
                code = rawCode,
                apis = apiManager.getApis(),
                project = this
            )
             */
            eventManager.fireEventWithContext(Events.Project.ProjectCompileEvent(project = this))
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
            if (info.isEnabled == enabled) return true
            info.isEnabled = enabled
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
    fun saveInfo() = runBlocking {
        withContext(Dispatchers.IO) {
            flowOf(json.encodeToString(info))
                .flowOn(Dispatchers.Default)
                .collect(File(directory.path, INFO_FILE_NAME)::writeText)
        }
        eventManager.fireEventWithContext(Events.Project.ProjectInfoUpdateEvent(project = this@Project))
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
     * Returns the scope instance if project is compiled, else null.
     *
     * @return scope returned on compile
     */
    fun getScope(): Any? {
        return langScope
    }

    /**
     * Count of jobs currently running in thread pool of this project.
     *
     * @return the size of running jobs.
     */
    fun activeJobs(): Int {
        return if (context == null || threadPoolName == null) 0
        else JobLocker.withParent(threadPoolName!!).activeJobs()
    }


    /**
     * Cancels all running jobs.
     */
    fun stopAllJobs() {
        if (context != null) {
            if (threadPoolName != null) {
                JobLocker.withParent(threadPoolName!!).purge()
            }
            (context as CoroutineDispatcher).cancel()
            context = null
        }
    }

    /**
     * Cancels all running jobs and releases [langScope] if the project is compiled.
     *
     * @param requestUpdate if *true*, requests update of UI
     */
    fun destroy(requestUpdate: Boolean = false) {
        stopAllJobs()
        if (langScope != null) {
            if (lang.requireRelease)
                lang.release(langScope!!)
            langScope = null
        }
        if (requestUpdate) requestUpdate()
    }

    fun getDataDirectory(): File = with(directory.resolve("data")) {
        if (!exists() || !isDirectory) mkdirs()
        this
    }

    private fun getThreadPoolSize(): Int = config.getCategory("beta_features").getInt("thread_pool_size", DEF_THREAD_POOL_SIZE)

    fun isEventCallAllowed(eventId: String): Boolean = info.allowedEventIds.isEmpty() || eventId in info.allowedEventIds
}