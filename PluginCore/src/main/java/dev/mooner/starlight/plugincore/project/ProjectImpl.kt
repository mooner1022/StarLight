/*
 * ProjectImpl.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.RuntimeClassLoader
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.config.data.FileConfig
import dev.mooner.starlight.plugincore.config.data.MutableConfig
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.logger.ProjectLogger
import dev.mooner.starlight.plugincore.logger.internal.Logger
import dev.mooner.starlight.plugincore.pipeline.SimplePipeline
import dev.mooner.starlight.plugincore.pipeline.stage.PipelineStage
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycle
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.currentThread
import dev.mooner.starlight.plugincore.utils.hasFile
import dev.mooner.starlight.plugincore.utils.require
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.CoroutineContext

class ProjectImpl private constructor(
    override val directory: File,
    private var mInfo: ProjectInfo
): Project() {

    override val info: ProjectInfo
        get() = mInfo

    override val config: MutableConfig =
        FileConfig(File(directory, CONFIG_FILE_NAME))

    override var threadPoolName: String? = null

    override val coroutineContext get() = mContext!!

    private val lifecycle = ProjectLifecycle(this)
    override fun getLifecycle(): ProjectLifecycle =
        lifecycle

    private var mContext: CoroutineContext? = null

    override val isCompiled: Boolean
        get() = langScope != null
    private var langScope: Any? = null
    private val lang: Language =
        Session.languageManager
            .getLanguage(info.languageId, newInstance = true)
            ?: throw IllegalArgumentException("Unable to find language ${info.languageId} for project '${info.name}'")

    override val logger: ProjectLogger = if (directory.hasFile(LOGS_FILE_NAME)) {
        ProjectLogger.fromFile(File(directory, LOGS_FILE_NAME))
    } else {
        ProjectLogger.create(directory)
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
    override fun callFunction(name: String, args: Array<out Any>, onException: (Throwable) -> Unit) {
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

        //setClassLoader()
        if (threadPoolName == null || mContext == null)
            mContext = createContext()

        fun onError(e: Throwable) {
            logger.e(tag, "Error while running: $e")
            val key = "shutdown_on_error"
            val shutdownOnError: Boolean = config["general"].getBoolean(key, true)

            if (shutdownOnError) {
                logger.e(info.name, "Shutting down project '${info.name}'...")
                info.isEnabled = false
                saveInfo()
                if (langScope != null && lang.requireRelease) {
                    lang.release(langScope!!)
                    langScope = null
                }
            }
            e.printStackTrace()
            //projectManager.onStateChanged(this)
            onException(e)
        }

        CoroutineScope(mContext!!).launch {
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
    }

    override fun compile(throwException: Boolean): Boolean = runBlocking {
        try {
            compileAsync().collect()
            true
        } catch (e: Throwable) {
            if (throwException) throw e
            false
        }
    }

    /**
     * Compiles the project with configured values.
     */
    override fun compileAsync(): Flow<Pair<PipelineStage<*, *>, Int>> =
        callbackFlow {
            val size: Int
            val pipeline = (getLanguage().toPipeline() as SimplePipeline).apply {
                size = this.stages.size
                setOnStageUpdateListener { stage, index ->
                    val percentage = ((index.toFloat() / size.toFloat()) * 100f).toInt()
                    trySend(stage to percentage)
                }
            }

            try {
                val code = directory.resolve(info.mainScript)
                    .require(File::isFile) { "Main script ${info.mainScript} is not a file." }
                    .runCatching(File::readText)
                    .getOrElse(::error)

                /*
                val rawCode: String = (directory.listFiles()?.find { it.isFile && it.name == info.mainScript }?: throw IllegalArgumentException(
                    "Cannot find main script ${info.mainScript} for project ${info.name}"
                )).readText(Charsets.UTF_8)
                 */
                if (lang.requireRelease && langScope != null) {
                    lang.release(langScope!!)
                    logger.v(tag, "engine released")
                }
                logger.i(translate {
                    Locale.ENGLISH { "Compiling project ${info.name}..." }
                    Locale.KOREAN  { "프로젝트 ${info.name} 컴파일 중..." }
                })
                //setClassLoader()
                langScope = pipeline.run(this@ProjectImpl to code)

                /*
                langScope = lang.compile(
                    code = rawCode,
                    apis = apiManager.getApis(),
                    project = this
                )
                 */
                EventHandler.fireEvent(Events.Project.Compile(project = this@ProjectImpl))
                //projectManager.onStateChanged(this)
            } catch (e: Exception) {
                e.printStackTrace()
                logger.e(tag, e.toString())
                throw e
            } finally {
                close()
            }
            awaitClose()
        }.flowOn(createContext())

    override fun setEnabled(enabled: Boolean): Boolean {
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
    override fun requestUpdate() {
        EventHandler.fireEventWithScope(Events.Project.InfoUpdate(project = this))
    }

    /**
     * Saves contents of [info] to a file.
     */
    override fun saveInfo() {
        runBlocking {
            val json = Json {
                encodeDefaults = true
                prettyPrint = true
            }

            flowOf(json.encodeToString(info))
                .onEach(File(directory.path, INFO_FILE_NAME)::writeText)
                .launchIn(CoroutineScope(Dispatchers.IO))

            EventHandler.fireEventWithScope(Events.Project.InfoUpdate(project = this@ProjectImpl))
        }
    }

    override fun loadInfo() {
        this.mInfo = File(directory.path, INFO_FILE_NAME)
            .readText()
            .let(json::decodeFromString)
    }

    /**
     * Returns the language used in the project.
     *
     * @return language used in the project.
     */
    override fun getLanguage(): Language {
        return lang
    }

    /**
     * Returns the scope instance if project is compiled, else null.
     *
     * @return scope returned on compile
     */
    override fun getScope(): Any? {
        return langScope
    }

    /**
     * Count of jobs currently running in thread pool of this project.
     *
     * @return the size of running jobs.
     */
    override fun activeJobs(): Int {
        return if (mContext == null || threadPoolName == null) 0
        else JobLocker.withParent(threadPoolName!!).activeJobs()
    }


    /**
     * Cancels all running jobs.
     */
    override fun stopAllJobs() {
        if (mContext != null) {
            if (threadPoolName != null) {
                JobLocker.withParent(threadPoolName!!).purge()
            }
            (mContext as CoroutineDispatcher).cancel()
            mContext = null
        }
    }

    /**
     * Cancels all running jobs and releases [langScope] if the project is compiled.
     *
     * @param requestUpdate if *true*, requests update of UI
     */
    override fun destroy(requestUpdate: Boolean) {
        logger.v("Destroying project [${info.name}]...")
        stopAllJobs()
        if (langScope != null) {
            if (lang.requireRelease)
                lang.release(langScope!!)
            langScope = null
        }
        if (requestUpdate) requestUpdate()
    }

    override fun getDataDirectory(): File = with(directory.resolve("data")) {
        if (!exists() || !isDirectory) mkdirs()
        this
    }

    override fun getClassLoader(): ClassLoader {
        val originalLoader = currentThread.contextClassLoader
        return RuntimeClassLoader(originalLoader)
    }

    override fun isEventCallAllowed(eventId: String): Boolean =
        info.allowedEventIds.isNotEmpty() && eventId in info.allowedEventIds

    @OptIn(DelicateCoroutinesApi::class)
    private fun createContext(): CoroutineContext {
        if (threadPoolName == null || mContext == null) {
            if (mContext != null) {
                mContext?.cancel()
                mContext = null
            }
            threadPoolName = "$tag-worker"
            val poolSize = getThreadPoolSize()
            mContext = newFixedThreadPoolContext(poolSize, threadPoolName!!)
            Logger.v(tag, "Allocated thread pool $threadPoolName with $poolSize threads to project ${info.name}")
        }
        return mContext!!
    }

    private fun getThreadPoolSize(): Int =
        config.category("beta_features")
            .getInt("thread_pool_size", DEF_THREAD_POOL_SIZE)

    private fun notifyStateUpdated(state: ProjectLifecycle.State) {
        getLifecycle()
    }

    companion object {
        private const val CONFIG_FILE_NAME  = "config.json"
        private const val INFO_FILE_NAME    = "project.json"
        private const val LOGS_FILE_NAME    = "logs-local.json"

        private const val DEF_THREAD_POOL_SIZE = 3

        fun create(dir: File, info: ProjectInfo): Project {
            val folder = File(dir.path, info.name)
                .also(File::mkdirs)

            File(folder.path, INFO_FILE_NAME)
                .writeText(json.encodeToString(info), Charsets.UTF_8)

            File(folder.path, info.mainScript)
                .writeText(getLanguage(info).defaultCode, Charsets.UTF_8)
            return loadFrom(folder, info)
        }

        fun loadFrom(directory: File, info: ProjectInfo): Project {
            return ProjectImpl(directory, info)
        }

        private fun getLanguage(info: ProjectInfo): Language =
            Session.languageManager.getLanguage(info.languageId)
                ?: throw IllegalArgumentException("Cannot find language ${info.languageId} for project '${info.name}'")
    }
}