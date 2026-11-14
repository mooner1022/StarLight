/*
 * JSNeon.kt created by Minki Moon(mooner1022) on 26. 10. 8. 오전 12:08
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.languages.neonjs

import android.widget.Toast
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.spinner
import dev.mooner.configdsl.options.toggle
import dev.mooner.neonjs.*
import dev.mooner.neonjs.net.NetExtension
import dev.mooner.neonjs.net.NetOptions
import dev.mooner.starlight.BuildConfig
import dev.mooner.starlight.api.msgbot.GlobalArgs
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.languages.EntryPoints
import dev.mooner.starlight.languages.ScriptErrors
import dev.mooner.starlight.languages.ScriptHostPolicy
import dev.mooner.starlight.languages.ScriptJson
import dev.mooner.starlight.languages.TimerDelay
import dev.mooner.starlight.plugincore.RuntimeClassLoader
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.language.CodeGenerator
import dev.mooner.starlight.plugincore.language.DefaultJSCodeGenerator
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.currentThread
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.plugincore.utils.verboseTranslated
import dev.mooner.starlight.script.errors.UncaughtErrors
import dev.mooner.starlight.utils.callPlugin
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds

private val LOG = LoggerFactory.logger {  }

class JSNeon: Language() {

    override val id: String = "js_neon"

    override val name: String = "JS(NeonJS)"

    override val fileExtension: String = "js"

    override val requireRelease: Boolean = false

    override val defaultConcurrency: Int = 1

    override val configStructure: ConfigStructure = configs

    override val codeGenerator: CodeGenerator = ModuleCodeGenerator(DefaultJSCodeGenerator())

    // The engine's own Java global stands in for StarLight's.
    override val nativeGlobals: Set<String> = setOf("Java")

    private val eventLoops: MutableMap<NeonContext, EventLoop> = ConcurrentHashMap()

    /** The guards of the versions that have an `onUncaughtError`, each by its context. */
    private val eventGuards: MutableMap<NeonContext, NeonEventGuard> = ConcurrentHashMap()

    override fun compile(
        code: String,
        apis: List<Api<*>>,
        project: Project?,
        classLoader: ClassLoader?
    ): Any {
        val langConf = getLanguageConfig()

        val isOptimized = BuildConfig.SCRIPT_JIT && langConf.getBoolean(CONF_OPTIMIZE_CODE, false)
        val execMode = if (isOptimized)
            langConf.getInt(CONF_OPTIMIZATION_LEVEL, DEF_OPTIMIZATION_LEVEL)
                .let(ExecutionMode.entries::get)
        else
            ExecutionMode.INTERPRETER

        val engine = NeonEngine.builder()
            .executionMode(execMode)
            .optimizingTier(isOptimized && langConf.getBoolean(CONF_ADVANCED_OPTIMIZATION, false))
            .sandbox(
                SandboxPolicy.builder()
                    .exposeJavaGlobal(true)
                    .build()
            )
            .hostAccess(hostAccess)
            .webGlobals(true)
            .extension(
                NetExtension(
                    NetOptions.builder()
                        .allowAllHosts()
                        .allowAddresses("127.0.0.1/32")
                        .allowInsecure(true)
                        .build()
                )
            )
            .console(project?.let(::createConsole) ?: NeonConsole.STDIO)
            .build()

        val context = engine.newContext()
        return try {
            withClassLoader(classLoader) {
                // API objects that keep script data take the context's own JSON as they are made.
                ScriptJson.providing(NeonJson(context)) {
                    for (api in apis) {
                        when (api.instanceType) {
                            InstanceType.CLASS ->
                                context.exposeClass(api.name, api.instanceClass)
                            InstanceType.OBJECT -> {
                                if (project == null)
                                    continue
                                // An API that fails to make its object, as one of a plugin, is left out; the script
                                // compiles without it, and one that uses it fails on the name, with this in the log.
                                callPlugin({ project.logger.e(translate {
                                    Locale.ENGLISH { "API '${api.name}' couldn't be loaded and is left out: $it" }
                                    Locale.KOREAN  { "API '${api.name}'을(를) 불러오지 못해 빼고 컴파일해요: $it" }
                                }) }) {
                                    context[api.name] = api.getInstance(project)
                                }
                            }
                        }
                    }
                }
                if (project != null)
                    context.defineGlobals(project)

                if (langConf.getBoolean(CONF_LOAD_EXT_MODULES, true)) {
                    LOG.verboseTranslated {
                        Locale.ENGLISH { "[Load external modules] Option enabled" }
                        Locale.KOREAN  { "[외부 모듈 로드] 설정 활성화됨" }
                    }
                    val isSandboxed = langConf.getBoolean(CONF_EXT_MODULE_SANDBOX, true)
                    context.setModuleLoader(FileModuleLoader(getModulePaths(project), isSandboxed))
                }

                val moduleName = project
                    ?.run { directory.resolve(info.mainScript).canonicalPath }
                    ?: name
                val module = context.evalModule(code, moduleName)
                if (project != null) {
                    context.setUncaughtErrorHandler { error, rejection ->
                        val type = if (rejection) "Unhandled promise rejection" else "Uncaught exception"
                        project.logger.error("$type: ${ScriptErrors.describe(error, project.directory)}")
                        if (project.runs(context))
                            UncaughtErrors.report(project.info.id, error,
                                if (rejection) UncaughtErrors.Origin.REJECTION else UncaughtErrors.Origin.EXCEPTION, "promise")
                    }
                    eventLoops[context] = EventLoop(context, project).apply { start() }
                    // The app reaches only what the module exports.
                    EntryPoints.report(project, code, exportsOnly = true) { name -> module.getMember(name).isFunction }
                    val projectId = project.info.id
                    val handler = neonUncaughtHandler(context, module,
                        succeeded = { handler -> UncaughtErrors.handlerSucceededLater(projectId, handler) },
                        failed = { handler, error -> UncaughtErrors.handlerFailedLater(projectId, handler, error) })
                    // Only a version with a handler has its event functions called through the guard, which gives the
                    // handler the function and the arguments of an async one that rejects.
                    if (handler != null)
                        eventGuards[context] = NeonEventGuard(context) { functionName, args, error ->
                            // As the engine words one it reports itself, which this one no longer is.
                            project.logger.error("Unhandled promise rejection: ${ScriptErrors.describe(error, project.directory)}")
                            if (project.runs(context))
                                UncaughtErrors.report(projectId, error, UncaughtErrors.Origin.REJECTION, functionName, args)
                        }
                    UncaughtErrors.watch(project, handler)
                }
                module
            }
        } catch (e: Throwable) {
            eventLoops -= context
            eventGuards -= context
            project?.let { UncaughtErrors.clear(it.info.id) }
            context.close()
            throw e
        }
    }

    /** Whether [context] is the one of the version that runs now: errors of one replaced since aren't its handler's. */
    private fun Project.runs(context: NeonContext): Boolean =
        (getScope() as? NeonValue)?.context === context

    override fun destroy(scope: Any) {
        val context = (scope as NeonValue).context
        eventLoops.remove(context)?.stop()
        eventGuards -= context
        context.interrupt()
        context.close()
        context.engine.close()
    }

    override fun callFunction(
        scope: Any,
        functionName: String,
        args: Array<out Any>
    ): Any? {
        val module = scope as NeonValue
        val function = module.getMember(functionName)
        if (!function.isFunction) {
            LOG.verboseTranslated {
                Locale.ENGLISH { "WARN: Unable to locate function: $functionName" }
                Locale.KOREAN  { "경고: 일치하는 함수를 찾을 수 없음: $functionName" }
            }
            return null
        }
        val guard = eventGuards[module.context]
            ?.takeIf { functionName != UncaughtErrors.FUNCTION }
        return withClassLoader(null) {
            guard?.call(function, functionName, args) ?: function.call(*args)
        }.`as`(Any::class.java)
    }

    override fun eval(code: String, options: Map<String, String>): Any {
        val allowJavaAccess   = options["allowJavaAccess"]?.toBoolean() ?: true
        val allowApiAccess    = options["allowApiAccess"]?.toBoolean() ?: false
        val optimizationLevel = options["optimizationLevel"]?.toIntOrNull() ?: 0

        val engine = NeonEngine.builder()
            .let { if (BuildConfig.SCRIPT_JIT) it.optimizationLevel(optimizationLevel) else it.executionMode(ExecutionMode.INTERPRETER) }
            .sandbox(
                SandboxPolicy.builder()
                    .exposeJavaGlobal(allowJavaAccess)
                    .build()
            )
            .hostAccess(if (allowJavaAccess) hostAccess else HostAccess.NONE)
            .webGlobals(true)
            .build()

        return engine.newContext().use { context ->
            if (allowJavaAccess && allowApiAccess) {
                for (api in Session.apiManager.getApis()) {
                    if (api.instanceType == InstanceType.OBJECT || api.name in nativeGlobals)
                        continue
                    context.exposeClass(api.name, api.instanceClass)
                }
            }
            val result = withClassLoader(null) {
                context.eval(code, "eval")
            }
            when {
                result.isHostObject -> result.asHostObject<Any>()
                result.isBoolean    -> result.asBoolean()
                result.isNumber     -> result.asDouble()
                else                -> result.toString()
            }
        }
    }

    private fun createConsole(project: Project) = NeonConsole { level, message ->
        when (level) {
            NeonConsole.Level.DEBUG -> project.logger.debug(message)
            NeonConsole.Level.LOG,
            NeonConsole.Level.INFO  -> project.logger.info(message)
            NeonConsole.Level.WARN  -> project.logger.warn(message)
            NeonConsole.Level.ERROR -> project.logger.error(message)
        }
    }

    private fun NeonContext.defineGlobals(project: Project) {
        val timers: MutableMap<Int, Job> = ConcurrentHashMap()
        val lastTimerId = AtomicInteger()

        fun startTimer(args: Array<NeonValue>, repeat: Boolean): Int {
            val kind = if (repeat) "setInterval" else "setTimeout"
            if (args.isEmpty())
                throw IllegalArgumentException("$kind requires at least 2 arguments")
            val callback = args[0]
                .takeIf(NeonValue::isFunction)
                ?: throw IllegalArgumentException("First argument must be a function")
            val mDelay = TimerDelay.effective(GlobalArgs.convertToInt(args.getOrNull(1)).toLong(), repeat)
            val params = args.drop(2).toTypedArray()

            val id = lastTimerId.incrementAndGet()
            // Named in the warning about a callback that runs long.
            val job = project.launch(CoroutineName(kind), start = CoroutineStart.LAZY) {
                do {
                    delay(mDelay.milliseconds)
                    runCatching {
                        withClassLoader(null) {
                            callback.call(*params)
                        }
                    }.onFailure { e ->
                        project.logger.error("Exception on timer $id: ${ScriptErrors.describe(e, project.directory)}")
                        if (project.runs(this@defineGlobals))
                            UncaughtErrors.report(project.info.id, e, UncaughtErrors.Origin.EXCEPTION, kind)
                    }
                } while (repeat && isActive)
            }.apply {
                invokeOnCompletion { timers -= id }
            }
            timers[id] = job
            job.start()
            return id
        }

        fun clearTimer(args: Array<NeonValue>) {
            val id = GlobalArgs.convertToInt(args.getOrNull(0))
            timers.remove(id)?.cancel()
        }

        // Makes the array `repeat` gives back, which is the engine's own.
        val arrayOfResults = eval("(function () { return Array.prototype.slice.call(arguments); })", "repeat-helper.js")

        fun repeatCall(args: Array<NeonValue>): Any? {
            if (args.size < 2)
                return null
            val function = args[0].takeIf(NeonValue::isFunction)
                ?: return null
            val count = GlobalArgs.convertToInt(args[1])
            if (count <= 0)
                return null
            val spread: Array<Any?> = args.getOrNull(2).let { extra ->
                when {
                    extra == null  -> if (args.size > 2) arrayOf<Any?>(null) else emptyArray<Any?>()
                    extra.isArray  -> Array(extra.arraySize.toInt()) { i -> extra.getElement(i.toLong()) }
                    else           -> arrayOf<Any?>(extra)
                }
            }
            return try {
                val results = ArrayList<Any?>(count.coerceAtMost(1024))
                for (n in 0 until count) {
                    results += try {
                        withClassLoader(null) { function.call(*spread) }
                    } catch (e: Exception) {
                        null
                    }
                }
                arrayOfResults.call(*results.toTypedArray())
            } catch (e: Exception) {
                null
            }
        }

        setFunction("setTimeout") { startTimer(it, repeat = false) }
        setFunction("setInterval") { startTimer(it, repeat = true) }
        setFunction("clearTimeout") { clearTimer(it); null }
        setFunction("clearInterval") { clearTimer(it); null }
        setFunction("repeat") { repeatCall(it) }
        setFunction("toast") { args ->
            val first = args.getOrNull(0)
            val message = when {
                first == null  -> "null"
                first.isString -> first.asString()
                else           -> first.toString()
            }
            val duration = GlobalArgs.convertToInt(args.getOrNull(1))

            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(GlobalApplication.requireContext(), message, duration).show()
            }
            null
        }

        // NeonJS's console has no clear; it empties the project's log, as Log.clear() does.
        runCatching {
            this["console"].putMember("clear", createFunction("clear") { project.logger.clear(); null })
        }
    }

    private fun getModulePaths(project: Project?): List<File> {
        val paths: MutableList<File> = arrayListOf()
        project?.directory?.let(paths::add)
        if (GlobalConfig.category("project").getBoolean("load_global_libraries", false))
            getStarLightDirectory()
                .resolve("modules")
                .let(paths::add)
        return paths
    }

    private class FileModuleLoader(
        private val paths: List<File>,
        private val isSandboxed: Boolean
    ): NeonModuleLoader {

        override fun resolve(specifier: String, referrer: String?): String {
            val parent = referrer
                ?.let(::File)
                ?.takeIf(File::isFile)
                ?.parentFile
            val bases = if (parent != null && specifier.startsWith("."))
                listOf(parent)
            else
                paths

            val file = bases.firstNotNullOfOrNull { it.resolve(specifier).findModule() }
                // Which file asked for it is what tells where to look when several import the same name.
                ?: throw IllegalArgumentException("Cannot find module '$specifier'" +
                    referrer?.let { " imported from ${File(it).name}" }.orEmpty())
            if (isSandboxed && paths.none { file.startsWith(it.canonicalFile) })
                throw SecurityException("Module '$specifier' is outside of module paths")
            return file.path
        }

        override fun load(key: String): String? =
            File(key)
                .takeIf(File::isFile)
                ?.readText()

        private fun File.findModule(): File? =
            sequenceOf(this, File("$path.js"), resolve(ENTRY_FILE))
                .map(File::getCanonicalFile)
                .firstOrNull(File::isFile)

        companion object {
            private const val ENTRY_FILE = "index.js"
        }
    }

    private class EventLoop(
        private val context: NeonContext,
        private val project: Project
    ) {

        private val isScheduled = AtomicBoolean()

        fun start() {
            context.setExternalJobListener(::schedule)
            schedule()
        }

        fun stop() {
            context.setExternalJobListener(null)
        }

        /** Runs what the engine has ready (fetch, socket, host future completions) as a task of the project. */
        private fun schedule() {
            if (!isScheduled.compareAndSet(false, true))
                return
            project.launch(CoroutineName(ASYNC_TASK_NAME)) {
                isScheduled.set(false)
                try {
                    withClassLoader(null) { context.runEventLoop(0) }
                } catch (e: Exception) {
                    if (e !is NeonInterruptedException && e !is IllegalStateException)
                        project.logger.error("Exception on event loop: ${ScriptErrors.describe(e, project.directory)}")
                }
            }.invokeOnCompletion { cause ->
                // Cancelled before it ran, as when the project's jobs are stopped: with the flag left set, nothing
                // the engine has ready would be run again.
                if (cause != null)
                    isScheduled.set(false)
            }
        }
    }

    private class ModuleCodeGenerator(
        private val generator: CodeGenerator
    ): CodeGenerator by generator {

        override fun generateFunction(
            name: String,
            arguments: Array<CodeGenerator.Argument<*>>,
            returns: KClass<*>?
        ): CodeGenerator.GeneratedFunction =
            generator.generateFunction(name, arguments, returns)
                .let { it.copy(function = "export ${it.function}") }
    }

    companion object {
        private const val CONF_OPTIMIZE_CODE = "optimize_code"
        private const val CONF_OPTIMIZATION_LEVEL = "optimization_level"
        private const val CONF_ADVANCED_OPTIMIZATION = "advanced_optimization"
        private const val CONF_LOAD_EXT_MODULES = "load_ext_modules"
        private const val CONF_EXT_MODULE_SANDBOX = "load_ext_module_sandbox"
        private const val DEF_OPTIMIZATION_LEVEL = 2 // Adaptive


        private val hostAccess: HostAccess = buildHostAccess(ScriptHostPolicy.isEnabled)

        /** What the event loop runs, as a warning about a run that takes long names it. */
        private val ASYNC_TASK_NAME: String
            get() = translate {
                Locale.ENGLISH { "code after await" }
                Locale.KOREAN  { "await 이후 코드" }
            }

        private val configs = config {
            category {
                id = "js_neon"
                title = "NeonJS"
                textColor = color { "#FFC069" }
                items {
                    toggle {
                        id = CONF_OPTIMIZE_CODE
                        title = "코드 최적화"
                        description = "코드를 컴파일 과정에서 최적화합니다. 컴파일 속도가 느려지지만 실행 속도가 빨라집니다."
                        defaultValue = false
                        icon = Icon.CHECK
                    }
                    spinner {
                        id = CONF_OPTIMIZATION_LEVEL
                        title = "최적화 레벨"
                        dependency = CONF_OPTIMIZE_CODE
                        icon = Icon.COMPRESS
                        iconTintColor = color { "#57837B" }
                        items = listOf("Interpreter", "Compiled", "Adaptive")
                        defaultIndex = DEF_OPTIMIZATION_LEVEL
                    }
                    toggle {
                        id = CONF_ADVANCED_OPTIMIZATION
                        dependency = CONF_OPTIMIZE_CODE
                        title = "고급 최적화"
                        description = "(실험적) 컴파일된 코드를 실행 중 수집한 타입 정보를 바탕으로 한 번 더 최적화합니다. 실행 속도가 빨라지지만 불안정할 수 있습니다."
                        defaultValue = false
                        icon = Icon.DEVELOPER_BOARD
                        iconTintColor = color { "#E5707E" }
                    }
                    toggle {
                        id = CONF_LOAD_EXT_MODULES
                        title = "외부 모듈 로드"
                        description = "컴파일 시 프로젝트 폴더 내의 모듈을 로드합니다."
                        defaultValue = true
                        icon = Icon.FOLDER
                        iconTintColor = color { "#C7B198" }
                    }
                    toggle {
                        id = CONF_EXT_MODULE_SANDBOX
                        dependency = CONF_LOAD_EXT_MODULES
                        title = "샌드박스 환경에서 로드"
                        description = "/modules 폴더 내의 모듈을 샌드박스 환경에서 실행합니다."
                        defaultValue = true
                        icon = Icon.LOCK
                        iconTintColor = color { "#F8B400" }
                    }
                }
            }
        }
    }
}

/**
 * Wraps a script's `onUncaughtError` so that a Promise it returns is followed from inside the context: one handled by
 * the wrapper isn't reported as unhandled. Leaves no global behind. The wrapper returns whether the call ended.
 */
private const val UNCAUGHT_GUARD = "(function (fn, sink) { return function () { var r = fn.apply(undefined, arguments); " +
    "if (r && typeof r.then === 'function') { r.then(function () { sink.done(); }, function (e) { sink.failed(e); }); " +
    "return false; } return true; }; })"

/**
 * Wraps an event function for one call, so that a Promise it returns is followed from inside the context: its rejection
 * goes to the sink, with the call it came from, instead of being reported as an unhandled one with no call to name.
 * What the function returns or throws is passed on as it is.
 */
private const val EVENT_GUARD = "(function (fn, sink) { return function () { var r = fn.apply(undefined, arguments); " +
    "if (r && typeof r.then === 'function') r.then(undefined, function (e) { sink.failed(e); }); return r; }; })"

/** What an event function's Promise was rejected with: the script's own value, which `onUncaughtError` is given. */
internal class ScriptRejection(val value: NeonValue) : RuntimeException() {

    private val text = runCatching { value.toString() }.getOrDefault("a rejected Promise")

    /** The script's stack, read as the engine's exceptions give theirs (`ScriptErrors.describe`). */
    val jsStack: String? = runCatching {
        value.takeIf { it.isObject }?.getMember("stack")?.takeIf { it.isString }?.asString()
    }.getOrNull()

    override val message: String
        get() = text

    override fun toString(): String =
        text
}

/** What the wrapper of one call of an event function tells of the Promise it returned; the script's engine calls it. */
internal class EventCallSink(
    private val functionName: String,
    private val args: Array<out Any>,
    private val onRejected: (functionName: String, args: Array<out Any>, error: ScriptRejection) -> Unit,
) {
    fun failed(error: NeonValue) =
        onRejected(functionName, args, ScriptRejection(error))
}

/**
 * Calls the event functions of a version that has an `onUncaughtError`: an `async` one that rejects is told to
 * [onRejected] with its name and arguments, on the context's thread. Leaves no global behind.
 */
internal class NeonEventGuard(
    context: NeonContext,
    private val onRejected: (functionName: String, args: Array<out Any>, error: ScriptRejection) -> Unit,
) {
    private val wrap = context.eval(EVENT_GUARD, "event-guard.js")

    fun call(function: NeonValue, functionName: String, args: Array<out Any>): NeonValue =
        wrap.call(function, EventCallSink(functionName, args, onRejected)).call(*args)
}

/** Makes the handler's `detail`, with `args` as a JS array. */
private const val UNCAUGHT_DETAIL = "(function (source, args) { return { source: source, args: Array.from(args) }; })"

/** What the wrapper around `onUncaughtError` tells of the Promise the function returned; the script's engine calls it. */
internal class UncaughtHandlerSink(
    private val onFulfilled: () -> Unit,
    private val onRejected: (Throwable) -> Unit,
) {
    fun done() =
        onFulfilled()

    fun failed(error: NeonValue) =
        // Read here, on the context's thread.
        onRejected(RuntimeException(runCatching { error.toString() }.getOrDefault("a rejected Promise")))
}

/**
 * The handler that calls [module]'s exported `onUncaughtError` in [context], or null when it exports none. The error
 * the script threw reaches it as the script's own value; another as the Java exception. A Promise it returns is told
 * of through [succeeded] or [failed] once it settles.
 */
internal fun neonUncaughtHandler(
    context: NeonContext,
    module: NeonValue,
    succeeded: (UncaughtErrors.Handler) -> Unit,
    failed: (UncaughtErrors.Handler, Throwable) -> Unit,
): UncaughtErrors.Handler? {
    val function = module.getMember(UncaughtErrors.FUNCTION)
    if (!function.isFunction)
        return null
    lateinit var handler: UncaughtErrors.Handler
    val sink = UncaughtHandlerSink({ succeeded(handler) }, { error -> failed(handler, error) })
    val guarded = context.eval(UNCAUGHT_GUARD, "onUncaughtError-guard.js").call(function, sink)
    val detail = context.eval(UNCAUGHT_DETAIL, "onUncaughtError-detail.js")
    handler = UncaughtErrors.Handler { error, origin, source, args ->
        val value: Any = generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }
            .take(8)
            .firstNotNullOfOrNull { (it as? ScriptRejection)?.value ?: (it as? NeonException)?.guestValue }
            ?: error
        withClassLoader(null) {
            guarded.call(value, origin, detail.call(source, args)).asBoolean()
        }
    }
    return handler
}

private inline fun <T> withClassLoader(classLoader: ClassLoader?, block: () -> T): T {
    val original = currentThread.contextClassLoader
    currentThread.contextClassLoader = classLoader ?: RuntimeClassLoader(original)
    return try {
        block()
    } finally {
        currentThread.contextClassLoader = original
    }
}
