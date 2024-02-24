/*
 * ProjectImpl.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.RuntimeClassLoader
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.config.data.FileConfig
import dev.mooner.starlight.plugincore.config.data.MutableConfig
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.logger.ProjectLogger
import dev.mooner.starlight.plugincore.logger.internal.Logger
import dev.mooner.starlight.plugincore.pipeline.SimplePipeline
import dev.mooner.starlight.plugincore.pipeline.stage.PipelineStage
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycle
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycleRegistry
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.currentThread
import dev.mooner.starlight.plugincore.utils.hasFile
import dev.mooner.starlight.plugincore.utils.require
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
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

    private var mContext: CoroutineContext? = null

    private val lifecycle = ProjectLifecycleRegistry(this)
    override fun getLifecycle(): ProjectLifecycle =
        lifecycle

    private val lifecycleRegistry: ProjectLifecycleRegistry
        get() = lifecycle

    private val _allowedEventIDs: MutableSet<String> = hashSetOf()
    val allowedEventIDs: Set<String> get() = _allowedEventIDs

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

        reloadAllowedEventIDs()
    }

    override fun callFunction(name: String, args: Array<out Any>, onException: (Throwable) -> Unit) {
        if (langScope == null) {
            onException(IllegalStateException("Project should be in compiled state before calling function."))
            return
        }

        if (threadPoolName == null || mContext == null)
            mContext = createContext()

        fun onError(e: Throwable) {
            logger.e(tag, translate {
                Locale.ENGLISH { "Error while running: $e" }
                Locale.KOREAN  { "실행 중 에러 발생: $e" }
            })
            val shutdownOnError = config
                .category("general")
                .getBoolean("shutdown_on_error", true)

            if (shutdownOnError) {
                logger.e(info.name, translate {
                    Locale.ENGLISH { "Destroying project '${info.name}'..." }
                    Locale.KOREAN  { "프로젝트 '${info.name}' 종료중..." }
                })
                destroy(requestUpdate = true)
            }
            e.printStackTrace()
            onException(e)
        }

        CoroutineScope(mContext!!).launch {
            try {
                JobLocker.withLock(threadPoolName!!) {
                    try {
                        lang.callFunction(langScope!!, name, args)
                    } catch (e: Error) {
                        onError(e)
                    }
                }
                if (isCompiled && lang.requireRelease)
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
            val orgLifecycleState = lifecycle.state
            try {
                lifecycleRegistry.setCurrentState(ProjectLifecycle.State.COMPILING)
                val code = directory.resolve(info.mainScript)
                    .require(File::isFile) { "Main script ${info.mainScript} is not a file." }
                    .runCatching(File::readText)
                    .getOrElse(::error)

                /*
                val rawCode: String = (directory.listFiles()?.find { it.isFile && it.name == info.mainScript }?: throw IllegalArgumentException(
                    "Cannot find main script ${info.mainScript} for project ${info.name}"
                )).readText(Charsets.UTF_8)
                 */
                if (isCompiled) {
                    if ("starlight.project.compile" in allowedEventIDs)
                        runCatching {
                            lang.callFunction(langScope!!, "onStartCompile", emptyArray())
                        }
                    if (lang.requireRelease) {
                        lang.release(langScope!!)
                        logger.v(tag, "engine released")
                    }
                    withTimeoutOrNull(200L) {
                        stopAllJobs()
                    }
                }
                logger.i(translate {
                    Locale.ENGLISH { "Compiling project ${info.name}..." }
                    Locale.KOREAN  { "프로젝트 ${info.name} 컴파일 중..." }
                })
                //setClassLoader()
                langScope = pipeline.run(this@ProjectImpl to code)
                EventHandler.fireEvent(Events.Project.Compile(project = this@ProjectImpl))
                lifecycleRegistry.setCurrentState(ProjectLifecycle.State.DISABLED)
                //projectManager.onStateChanged(this)
            } catch (e: Exception) {
                e.printStackTrace()
                logger.e(tag, "ECOMF: $e")
                lifecycleRegistry.setCurrentState(orgLifecycleState)
                throw e
            } finally {
                requestUpdate()
                close()
            }
            awaitClose()
        }.flowOn(createContext())

    override fun setEnabled(enabled: Boolean): Boolean {
        if (!isCompiled)
            return false
        if (info.isEnabled != enabled) {
            info.isEnabled = enabled
            saveInfo()
            val state = if (enabled)
                ProjectLifecycle.State.ENABLED
            else
                ProjectLifecycle.State.DISABLED
            lifecycleRegistry.setCurrentState(state)
            requestUpdate()
        }
        return true
    }

    override fun requestUpdate() {
        EventHandler.fireEventWithScope(Events.Project.InfoUpdate(project = this))
    }

    override fun saveInfo() = runBlocking {
        val json = Json {
            encodeDefaults = true
            prettyPrint = true
        }

        flowOf(json.encodeToString(info))
            .onEach(File(directory.path, INFO_FILE_NAME)::writeText)
            .launchIn(CoroutineScope(Dispatchers.IO))

        reloadAllowedEventIDs()
    }

    override fun loadInfo() {
        this.mInfo = File(directory.path, INFO_FILE_NAME)
            .readText()
            .let(json::decodeFromString)
    }

    override fun getLanguage(): Language {
        return lang
    }

    override fun getScope(): Any? {
        return langScope
    }

    override fun activeJobs(): Int {
        return if (mContext == null || threadPoolName == null) 0
        else JobLocker.withParent(threadPoolName!!).activeJobs()
    }

    override fun stopAllJobs() {
        if (mContext != null) {
            if (threadPoolName != null) {
                JobLocker.withParent(threadPoolName!!).purge()
            }
            (mContext as CoroutineDispatcher).cancel()
            mContext = null
        }
    }

    override fun destroy(requestUpdate: Boolean) {
        logger.v("Destroying project [${info.name}]...")
        stopAllJobs()
        langScope?.let { scope ->
            if (lang.requireRelease)
                lang.release(scope)
            lang.destroy(scope)
            langScope = null
        }
        if (info.isEnabled) {
            info.isEnabled = false
            saveInfo()
        }
        lifecycleRegistry.setCurrentState(ProjectLifecycle.State.DESTROYED)

        if (requestUpdate)
            requestUpdate()
    }

    override fun rename(name: String, preserveMainScript: Boolean) {
        val mainScript = if (preserveMainScript)
            info.mainScript
        else {
            val nName = name + "." + lang.fileExtension
            File(directory, info.mainScript)
                .renameTo(File(directory, nName))
            nName
        }
        val orgName = info.name
        val nInfo = info.copy(
            name       = name,
            mainScript = mainScript
        )
        mInfo = nInfo

        projectManager.projects.let {
            it -= orgName
            it[info.name] = this
        }

        saveInfo()
        requestUpdate()
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
        allowedEventIDs.isNotEmpty() && eventId in allowedEventIDs

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

    /*
    private fun notifyStateUpdated(state: ProjectLifecycle.State) {
        getLifecycle()
    }
     */

    private fun reloadAllowedEventIDs() {
        if (_allowedEventIDs.isNotEmpty())
            _allowedEventIDs.clear()
        ProjectEventManager
            .filterAllowedEvents(info.allowedEventIds)
            .forEach(_allowedEventIDs::add)
    }

    companion object {
        private const val CONFIG_FILE_NAME  = "config.json"
        private const val INFO_FILE_NAME    = "project.json"
        private const val LOGS_FILE_NAME    = "logs-local.json"

        private const val DEF_THREAD_POOL_SIZE = 3

        fun create(dir: File, info: ProjectInfo, events: Map<String, ProjectEvent>): Project {
            val folder = File(dir.path, info.name)
                .also(File::mkdirs)

            File(folder.path, INFO_FILE_NAME)
                .writeText(json.encodeToString(info), Charsets.UTF_8)

            File(folder.path, info.mainScript)
                .writeText(getLanguage(info).formatDefaultCode(info.mainScript, events.values.toList()), Charsets.UTF_8)

            for ((eventId, _) in events)
                info.allowedEventIds += eventId

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