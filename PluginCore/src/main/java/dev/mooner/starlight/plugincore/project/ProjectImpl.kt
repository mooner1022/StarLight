/*
 * ProjectImpl.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

import android.util.AtomicFile
import androidx.core.util.readText
import androidx.core.util.writeText
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
import dev.mooner.starlight.plugincore.project.event.getInstance
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycle
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycleRegistry
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.currentThread
import dev.mooner.starlight.plugincore.utils.hasFile
import dev.mooner.starlight.plugincore.utils.require
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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

    override val coroutineContext: CoroutineContext
        get() = obtainExecutor().scope.coroutineContext

    @Volatile
    private var executor: ProjectExecutor? = null

    private val lastDropWarning = AtomicLong()

    private val infoWriteScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))

    private val lifecycle = ProjectLifecycleRegistry(this)
    override fun getLifecycle(): ProjectLifecycle =
        lifecycle

    private val lifecycleRegistry: ProjectLifecycleRegistry
        get() = lifecycle

    private val _allowedEventIDs: MutableSet<String> = hashSetOf()
    val allowedEventIDs: Set<String> get() = _allowedEventIDs

    override val isCompiled: Boolean
        get() = langScope != null

    // Written by a compile and by a destroy, read by the threads that run events.
    @Volatile
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

    override fun callFunction(name: String, args: Array<out Any>, onException: (Throwable) -> Unit) =
        runEvent({
            // The scope as it is when the call runs: one queued before a compile is for the new code.
            val scope = langScope
                ?: throw IllegalStateException("Project should be in compiled state before calling function.")
            try {
                lang.callFunction(scope, name, args)
            } finally {
                // Only a called function leaves the language's scope entered on this thread.
                if (lang.requireRelease)
                    lang.release(scope)
            }
        }, onException)

    override fun runEvent(task: () -> Unit, onException: (Throwable) -> Unit) {
        if (langScope == null) {
            onException(IllegalStateException("Project should be in compiled state before calling function."))
            return
        }

        /** [scope] is the version the event ran in: the one there was when it started, not when it was sent. */
        fun onError(e: Throwable, scope: Any?) {
            // The version this event was for is gone: a compile replaced it, or took it down by failing. Its
            // error, often the engine stopping it, says nothing about the version that runs now, which stays.
            if (langScope !== scope) {
                logger.d(tag, translate {
                    Locale.ENGLISH { "An event of the version that ran before the last compile ended with: $e" }
                    Locale.KOREAN  { "마지막 컴파일 전 버전의 이벤트가 오류로 끝났어요: $e" }
                })
                onException(e)
                return
            }
            val shutdownOnError = config
                .category("general")
                .getBoolean("shutdown_on_error", true)
            // One entry: what went wrong, where, and what was done about it. It used to be two errors here and a
            // third from whoever sent the event, each an alert of its own saying the same.
            val what = ScriptErrorText.of(e, directory)
            logger.e(tag, translate {
                Locale.ENGLISH { "Error while running: $what" + if (shutdownOnError) "\nThe project was turned off, as its setting for errors says." else "" }
                Locale.KOREAN  { "실행 중 에러 발생: $what" + if (shutdownOnError) "\n'오류 발생시 비활성화' 설정에 따라 프로젝트를 껐어요." else "" }
            })
            if (shutdownOnError)
                destroy(requestUpdate = true)
            e.printStackTrace()
            onException(e)
        }

        obtainExecutor().post {
            JobLocker.withLock(threadPoolName!!) {
                val scope = langScope
                runCatching(task)
                    .onFailure { onError(it, scope) }
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
            // One compile of the project at a time, whoever started it (the app, a script, StarLight AI): two at once
            // would each release and swap the scope the other is building. One stuck past the wait fails this one.
            // Noted inside the block: cancelled just as the permit is taken, runInterruptible throws instead of returning
            // it, and it is given back here.
            var acquired = false
            try {
                runInterruptible { acquired = compilePermit.tryAcquire(COMPILE_WAIT_SECONDS, TimeUnit.SECONDS) }
            } catch (e: Throwable) {
                if (acquired)
                    compilePermit.release()
                throw e
            }
            if (!acquired) {
                logger.e(tag, translate {
                    Locale.ENGLISH { "A compile of ${info.name} that started earlier is still running" }
                    Locale.KOREAN  { "먼저 시작된 ${info.name} 컴파일이 아직 진행 중이에요" }
                })
                throw IllegalStateException("A compile of ${info.name} that started earlier is still running " +
                    "after $COMPILE_WAIT_SECONDS seconds")
            }
            val orgLifecycleState = lifecycle.state
            // Whether the version that was running has been touched: from then on a failure can't leave it as it was.
            var tornDown = false
            try {
                // Read before anything is taken down, so a script that can't be read leaves the project as it is.
                val code = directory.resolve(info.mainScript)
                    .require(File::isFile) { "Main script ${info.mainScript} is not a file." }
                    .runCatching(File::readText)
                    .getOrElse(::error)

                tornDown = true
                // Observers end what the running version registered: API2 listeners, broadcast receivers.
                lifecycleRegistry.setCurrentState(ProjectLifecycle.State.COMPILING)
                val previousScope = langScope
                if (previousScope != null) {
                    if ("starlight.project.compile" in allowedEventIDs)
                        runCatching { lang.callFunction(previousScope, "onStartCompile", emptyArray()) }
                            // Logged and no more: a script that fails to save its state must still be replaceable.
                            .onFailure {
                                val what = ScriptErrorText.of(it, directory)
                                logger.e(tag, translate {
                                    Locale.ENGLISH { "onStartCompile failed; the compile goes on: $what" }
                                    Locale.KOREAN  { "onStartCompile에서 오류가 났어요. 컴파일은 계속해요: $what" }
                                })
                            }
                    if (lang.requireRelease) {
                        lang.release(previousScope)
                        logger.v(tag, "engine released")
                    }
                }
                // The running version's timers and queued events, and what a compile that failed part-way left.
                stopAllJobs()
                logger.i(translate {
                    Locale.ENGLISH { "Compiling project ${info.name}..." }
                    Locale.KOREAN  { "프로젝트 ${info.name} 컴파일 중..." }
                })
                langScope = pipeline.run(this@ProjectImpl to code)
                // Releasing alone keeps what a scope holds: NeonJS keeps its engine, event loop and open sockets.
                previousScope?.takeIf { it !== langScope }?.let { scope ->
                    runCatching { lang.destroy(scope) }
                        .onFailure { logger.w(tag, "Failed to destroy the previous scope: $it") }
                }
                EventHandler.fireEvent(Events.Project.Compile(project = this@ProjectImpl))
                // A project that is on runs from here; observers hear of it as they do when it is switched on.
                lifecycleRegistry.setCurrentState(
                    if (info.isEnabled) ProjectLifecycle.State.ENABLED else ProjectLifecycle.State.DISABLED)
            } catch (e: Throwable) {
                e.printStackTrace()
                logger.e(tag, "ECOMF: ${ScriptErrorText.of(e, directory)}")
                if (tornDown)
                    unloadAfterFailedCompile()
                else
                    lifecycleRegistry.setCurrentState(orgLifecycleState)
                throw e
            } finally {
                compilePermit.release()
                requestUpdate()
                close()
            }
            awaitClose()
        }.flowOn(Dispatchers.IO)

    /**
     * A compile failed after the running version was taken down for it: its listeners and timers were ended before
     * the new code ran, so what is left of it is not a bot that works. The project is unloaded, as one that was never
     * compiled, with what the failed code started (timers, listeners it added before it threw) ended too. It stays
     * switched on: not compiled, it counts as off, and the next compile that succeeds brings it back.
     */
    private fun unloadAfterFailedCompile() {
        val wasRunning = langScope != null
        runCatching { stopAllJobs() }
        langScope?.let { scope ->
            langScope = null
            runCatching { lang.destroy(scope) }
                .onFailure { logger.w(tag, "Failed to destroy the previous scope: $it") }
        }
        // Observers clear what the failed code registered, as they do for a project that is destroyed.
        lifecycleRegistry.setCurrentState(ProjectLifecycle.State.DESTROYED)
        // Not a warning of its own: the failure is already reported, with its cause, by whoever compiled.
        if (wasRunning)
            logger.i(tag, translate {
                Locale.ENGLISH { "The compile failed, so ${info.name} is stopped until it compiles again." }
                Locale.KOREAN  { "컴파일에 실패해 ${info.name}의 실행을 멈췄어요. 다시 컴파일되면 실행돼요." }
            })
    }

    /** Held while the project compiles; a semaphore, as the compile may go on on another thread than it started. */
    private val compilePermit = Semaphore(1)

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

    override fun saveInfo() {
        val encoded = infoJson.encodeToString(info)
        infoWriteScope.launch {
            AtomicFile(File(directory.path, INFO_FILE_NAME)).writeText(encoded)
        }

        reloadAllowedEventIDs()
    }

    override fun loadInfo() {
        this.mInfo = AtomicFile(File(directory.path, INFO_FILE_NAME))
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
        val current = executor ?: return 0
        return JobLocker.withParent(current.name).activeJobs()
    }

    override fun queuedEvents(): Int =
        executor?.queuedEvents ?: 0

    override fun stopAllJobs() {
        val current = synchronized(this) {
            executor.also { executor = null }
        } ?: return
        JobLocker.withParent(current.name).purge()
        current.shutdown()
    }

    override fun destroy(requestUpdate: Boolean) {
        release()
        if (info.isEnabled) {
            info.isEnabled = false
            saveInfo()
        }
        lifecycleRegistry.setCurrentState(ProjectLifecycle.State.DESTROYED)

        if (requestUpdate)
            requestUpdate()
    }

    /** Stops the project as the app shuts down, as after a crash; unlike [destroy], it stays on for the next start. */
    internal fun shutdown() {
        release()
        lifecycleRegistry.setCurrentState(ProjectLifecycle.State.DESTROYED)
    }

    private fun release() {
        logger.v("Destroying project [${info.name}]...")
        stopAllJobs()
        langScope?.let { scope ->
            // Gone whatever the language does with it: one of a plugin that throws here would otherwise leave the
            // project half stopped, and take down the thread that stops it.
            langScope = null
            if (lang.requireRelease)
                runCatching { lang.release(scope) }
                    .onFailure { logger.w(tag, "Failed to release the scope: $it") }
            runCatching { lang.destroy(scope) }
                .onFailure { logger.w(tag, "Failed to destroy the scope: $it") }
        }
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

    private fun obtainExecutor(): ProjectExecutor =
        executor ?: synchronized(this) {
            executor ?: ProjectExecutor("$tag-worker", getConcurrency(), ::onEventDropped).also {
                threadPoolName = it.name
                executor = it
                Logger.v(tag, "Allocated ${it.name} (concurrency ${it.concurrency}) to project ${info.name}")
            }
        }

    private fun getConcurrency(): Int =
        config.category("beta_features")
            .getInt("thread_pool_size", lang.defaultConcurrency)
            .coerceAtLeast(1)

    private fun onEventDropped() {
        val now = System.currentTimeMillis()
        val last = lastDropWarning.get()
        if (now - last < DROP_WARNING_INTERVAL_MILLIS || !lastDropWarning.compareAndSet(last, now))
            return
        logger.w(tag, translate {
            Locale.ENGLISH { "Event buffer is full, dropped the oldest events" }
            Locale.KOREAN  { "이벤트 버퍼가 가득 차 가장 오래된 이벤트를 버렸어요" }
        })
    }

    /*
    private fun notifyStateUpdated(state: ProjectLifecycle.State) {
        getLifecycle()
    }
     */

    private fun reloadAllowedEventIDs() {
        if (_allowedEventIDs.isNotEmpty())
            _allowedEventIDs.clear()
        receivedEventIds(info).forEach(_allowedEventIDs::add)
    }

    companion object {
        private const val CONFIG_FILE_NAME  = "config.json"
        private const val INFO_FILE_NAME    = "project.json"
        private const val LOGS_FILE_NAME    = "logs.json"

        private const val DROP_WARNING_INTERVAL_MILLIS = 5_000L

        /** How long a compile waits for one of the same project that is already running. */
        private const val COMPILE_WAIT_SECONDS = 60L

        private val infoJson = Json {
            encodeDefaults = true
            prettyPrint = true
        }

        fun create(dir: File, info: ProjectInfo, events: Map<String, ProjectEvent>): Project {
            val folder = File(dir.path, info.name)
                .also(File::mkdirs)

            for ((eventId, _) in events)
                info.allowedEventIds += eventId

            File(folder.path, INFO_FILE_NAME)
                .writeText(json.encodeToString(info), Charsets.UTF_8)

            // Every event it receives, not only those with starting code: a wildcard such as starlight.* lets in events
            // that call functions by name, which a listener's function mustn't take.
            val received = receivedEventIds(info)
                .mapNotNull { id -> ProjectEventManager.findFirst(id)?.let { runCatching { it.getInstance() }.getOrNull() } }
            File(folder.path, info.mainScript)
                .writeText(getLanguage(info).newProjectCode(info.mainScript, events.values.toList(), received), Charsets.UTF_8)

            return loadFrom(folder, info)
        }

        /** The IDs of the registered events that [info]'s allowed-event entries let in and its API levels have. */
        private fun receivedEventIds(info: ProjectInfo): List<String> =
            ProjectEventManager
                .filterAllowedEvents(info.allowedEventIds)
                .filter { Session.apiLevelManager.hasEvent(info.apiLevels, it) }

        fun loadFrom(directory: File, info: ProjectInfo): Project {
            return ProjectImpl(directory, info)
        }

        private fun getLanguage(info: ProjectInfo): Language =
            Session.languageManager.getLanguage(info.languageId)
                ?: throw IllegalArgumentException("Cannot find language ${info.languageId} for project '${info.name}'")
    }
}

/**
 * A project's share of the app's workers ([Dispatchers.IO]): its events wait in a bounded buffer and run on the shared
 * threads, at most [concurrency] at once, in arrival order when that is 1. What is launched in [scope] (timers,
 * plugins) runs under the same limit, between events.
 */
internal class ProjectExecutor(
    val name: String,
    val concurrency: Int,
    private val onDropped: () -> Unit,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(concurrency) + CoroutineName(name))

    @Volatile
    private var closed = false

    private val queued = AtomicInteger()

    /** Events in the buffer, not yet taken by a worker. */
    val queuedEvents: Int
        get() = queued.get()

    private val events = Channel<() -> Unit>(EVENT_BUFFER_SIZE, BufferOverflow.DROP_OLDEST) {
        queued.decrementAndGet()
        if (!closed) onDropped()
    }

    init {
        repeat(concurrency) {
            scope.launch {
                for (event in events) {
                    queued.decrementAndGet()
                    event()
                    yield()
                }
            }
        }
    }

    fun post(event: () -> Unit) {
        queued.incrementAndGet()
        if (events.trySend(event).isFailure)
            queued.decrementAndGet()
    }

    fun shutdown() {
        closed = true
        events.cancel()
        scope.cancel()
    }

    companion object {
        const val EVENT_BUFFER_SIZE = 256
    }
}