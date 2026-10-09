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
import dev.mooner.neonjs.ExecutionMode
import dev.mooner.neonjs.HostAccess
import dev.mooner.neonjs.NeonConsole
import dev.mooner.neonjs.NeonContext
import dev.mooner.neonjs.NeonEngine
import dev.mooner.neonjs.NeonModuleLoader
import dev.mooner.neonjs.NeonValue
import dev.mooner.neonjs.SandboxPolicy
import dev.mooner.starlight.core.GlobalApplication
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
import dev.mooner.starlight.plugincore.utils.currentThread
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.plugincore.utils.verboseTranslated
import dev.mooner.starlight.utils.isNoobMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds

private val LOG = LoggerFactory.logger {  }

class JSNeon: Language() {

    override val id: String = "js_neon"

    override val name: String = "JS(NeonJS)"

    override val fileExtension: String = "js"

    override val requireRelease: Boolean = false

    override val configStructure: ConfigStructure = configs

    override val codeGenerator: CodeGenerator = ModuleCodeGenerator(DefaultJSCodeGenerator())

    override fun compile(
        code: String,
        apis: List<Api<*>>,
        project: Project?,
        classLoader: ClassLoader?
    ): Any {
        val langConf = getLanguageConfig()

        val execMode = if (langConf.getBoolean(CONF_OPTIMIZE_CODE, false))
            langConf.getInt(CONF_OPTIMIZATION_LEVEL, DEF_OPTIMIZATION_LEVEL)
                .let(ExecutionMode.entries::get)
        else
            ExecutionMode.INTERPRETER

        val engine = NeonEngine.builder()
            .executionMode(execMode)
            .sandbox(
                SandboxPolicy.builder()
                    .exposeJavaGlobal(true)
                    .build()
            )
            .hostAccess(hostAccess)
            .webGlobals(true)
            .console(project?.let(::createConsole) ?: NeonConsole.STDIO)
            .build()

        val context = engine.newContext()
        return try {
            withClassLoader(classLoader) {
                for (api in apis.filter { it.isSupported }) {
                    when (api.instanceType) {
                        InstanceType.CLASS ->
                            context.exposeClass(api.name, api.instanceClass)
                        InstanceType.OBJECT -> {
                            if (project == null)
                                continue
                            context[api.name] = api.getInstance(project)
                        }
                    }
                }
                if (project != null)
                    context.defineGlobals(project)

                if (langConf.getBoolean(CONF_LOAD_EXT_MODULES, true) || isNoobMode) {
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
                context.evalModule(code, moduleName)
            }
        } catch (e: Throwable) {
            context.close()
            throw e
        }
    }

    override fun destroy(scope: Any) {
        val context = (scope as NeonValue).context
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
        return withClassLoader(null) {
            function.call(*args)
        }.`as`(Any::class.java)
    }

    override fun eval(code: String, options: Map<String, String>): Any {
        val allowJavaAccess   = options["allowJavaAccess"]?.toBoolean() ?: true
        val allowApiAccess    = options["allowApiAccess"]?.toBoolean() ?: false
        val optimizationLevel = options["optimizationLevel"]?.toIntOrNull() ?: 0

        val engine = NeonEngine.builder()
            .optimizationLevel(optimizationLevel)
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
                    if (api.instanceType == InstanceType.OBJECT || !api.isSupported)
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

    private val Api<*>.isSupported: Boolean
        get() = this.name != "Java" && SUPPORTED_API_PACKAGES.any { javaClass.name.startsWith("$it.") }

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
            val callback = args.getOrNull(0)
                ?.takeIf(NeonValue::isFunction)
                ?: throw IllegalArgumentException("Callback is not a function")
            val mDelay = args.getOrNull(1)
                ?.takeIf(NeonValue::isNumber)
                ?.asDouble()
                ?.toLong() ?: 0L
            val params = args.drop(2).toTypedArray()

            val id = lastTimerId.incrementAndGet()
            val job = project.launch(start = CoroutineStart.LAZY) {
                do {
                    delay(mDelay.milliseconds)
                    runCatching {
                        withClassLoader(null) { callback.call(*params) }
                    }.onFailure { e ->
                        project.logger.error("Exception on timer $id: $e")
                    }
                } while (repeat)
            }.apply {
                invokeOnCompletion { timers -= id }
            }
            timers[id] = job
            job.start()
            return id
        }

        fun clearTimer(args: Array<NeonValue>) {
            val id = args.getOrNull(0)
                ?.takeIf(NeonValue::isNumber)
                ?.asDouble()
                ?.toInt() ?: return
            timers.remove(id)?.cancel()
        }

        setFunction("setTimeout") { startTimer(it, repeat = false) }
        setFunction("setInterval") { startTimer(it, repeat = true) }
        setFunction("clearTimeout") { clearTimer(it); null }
        setFunction("clearInterval") { clearTimer(it); null }
        setFunction("toast") { args ->
            val message = args.getOrNull(0)?.toString() ?: ""
            val duration = args.getOrNull(1)
                ?.takeIf(NeonValue::isNumber)
                ?.asInt() ?: Toast.LENGTH_LONG

            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(GlobalApplication.requireContext(), message, duration).show()
            }
            null
        }
    }

    private fun getModulePaths(project: Project?): List<File> {
        val paths: MutableList<File> = arrayListOf()
        project?.directory?.let(paths::add)
        if (GlobalConfig.category("project").getBoolean("load_global_libraries", false) || isNoobMode)
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
                ?: throw IllegalArgumentException("Cannot find module '$specifier'")
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
        private const val CONF_LOAD_EXT_MODULES = "load_ext_modules"
        private const val CONF_EXT_MODULE_SANDBOX = "load_ext_module_sandbox"
        private const val DEF_OPTIMIZATION_LEVEL = 2 // Adaptive

        private val SUPPORTED_API_PACKAGES = setOf(
            "dev.mooner.starlight.api.original",
            "dev.mooner.starlight.api.api2",
            "dev.mooner.starlight.api.legacy",
        )

        private val hostAccess: HostAccess = HostAccess.builder(HostAccess.Level.ALL)
            .defaultDenyList(false)
            .allowLookup { true }
            .build()

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

private inline fun <T> withClassLoader(classLoader: ClassLoader?, block: () -> T): T {
    val original = currentThread.contextClassLoader
    currentThread.contextClassLoader = classLoader ?: RuntimeClassLoader(original)
    return try {
        block()
    } finally {
        currentThread.contextClassLoader = original
    }
}
