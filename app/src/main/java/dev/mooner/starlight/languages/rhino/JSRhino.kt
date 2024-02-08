package dev.mooner.starlight.languages.rhino

import android.net.Uri
import com.faendir.rhino_android.AndroidContextFactory
import com.faendir.rhino_android.RhinoAndroidHelper
import dev.mooner.starlight.plugincore.RuntimeClassLoader
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.config.ConfigStructure
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.plugincore.utils.currentThread
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.plugincore.utils.verboseTranslated
import dev.mooner.starlight.utils.isNoobMode
import dev.mooner.starlight.utils.toURI
import org.mozilla.javascript.*
import org.mozilla.javascript.Function
import org.mozilla.javascript.commonjs.module.Require
import org.mozilla.javascript.commonjs.module.RequireBuilder
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider
import java.io.File
import java.net.URI

private val LOG = LoggerFactory.logger {  }

class JSRhino: Language() {

    override val id: String = "JS_RHINO"

    override val name: String = translate {
        Locale.ENGLISH { "JavaScript(Rhino)" }
        Locale.KOREAN  { "자바스크립트(라이노)" }
    }

    override val fileExtension: String = "js"

    override val requireRelease: Boolean = true

    override val configStructure: ConfigStructure = configs

    override val defaultCode: String = """
            %HEADER%
            %BODY%
        """.trimIndent()

    //override fun onConfigUpdated(updated: Map<String, Any>) {}

    internal fun enterContext(): Context {
        val config = getLanguageConfig()
        val optLevel = if (config.getBoolean(CONF_OPTIMIZE_CODE, false))
            config.getInt(CONF_OPTIMIZATION_LEVEL, 0)
        else
            -1

        val context = createAndroidHelper().enterContext().apply {
            optimizationLevel = optLevel
            val langVersionIndex = config.getInt(CONF_LANG_VERSION)
            languageVersion = if (langVersionIndex == null)
                LANG_DEF_VERSION
            else
                indexToVersion(langVersionIndex)
            wrapFactory = PrimitiveWrapFactory()
        }
        //context.applicationClassLoader =
        //    RuntimeClassLoader(currentThread.contextClassLoader)
        return context
    }

    // Overrides the helper to set classloader as RuntimeClassLoader
    private fun createAndroidHelper(): RhinoAndroidHelper {
        return object : RhinoAndroidHelper(File(System.getProperty("java.io.tmpdir", "."), "classes")) {
            override fun createAndroidContextFactory(cacheDirectory: File?): AndroidContextFactory {
                val classLoader = RuntimeClassLoader(currentThread.contextClassLoader)
                return createAndroidContextFactory(cacheDirectory, classLoader)
            }
        }
    }

    override fun compile(code: String, apis: List<Api<*>>, project: Project?, classLoader: ClassLoader?): Any {
        val context = enterContext()
        val scope = if (project != null)
            context.initStandardObjects(RhinoGlobalObject(context, project))
        else
            context.initStandardObjects(ImporterTopLevel(context))

        //val scope = context.newObject(shared)
        //val engine = ScriptEngineManager().getEngineByName("rhino")!!
        if (project != null) {
            var importLines: StringBuilder? = null
            for(api in apis) {
                when(api.instanceType) {
                    InstanceType.CLASS -> {
                        val line = "const ${api.name} = Packages.${api.instanceClass.name};\n"
                        if (importLines == null)
                            importLines = StringBuilder(line)
                        else
                            importLines.append(line)
                        //context.evaluateString(scope, "const ${methodBlock.name} = ${methodBlock.instanceClass.name};", "import", 1, null)
                    }
                    InstanceType.OBJECT -> {
                        val instance = api.getInstance(project)
                        scope.put(api.name, scope, instance)
                        //ScriptableObject.putProperty(scope, methodBlock.name, Context.javaToJS(instance, scope))
                    }
                }
                //engine.put(methodBlock.blockName, methodBlock.instance)
            }
            if (importLines != null) {
                //Logger.v(importLines.toString())
                context.evaluateString(scope, importLines.toString(), "import", 1, null)
            }
        }

        val langConf = getLanguageConfig()
        if (langConf.getBoolean("load_ext_modules", false) || isNoobMode) {
            LOG.verboseTranslated {
                Locale.ENGLISH { "[Load external modules] Option enabled" }
                Locale.KOREAN  { "[외부 모듈 로드] 설정 활성화됨" }
            }
            val isSandboxed = langConf.getBoolean("load_ext_module_sandbox", false)
            val require = initRequire(context, scope, isSandboxed, project)
            require.install(scope)
        }

        //engine.eval(code)
        context.evaluateString(scope, code, project?.info?.name ?: name, 1, null)
        //scope.put()
        Context.exit()
        return scope
    }

    override fun release(scope: Any) {
        try {
            Context.exit()
        } catch (e: IllegalStateException) {
            LOG.verboseTranslated {
                Locale.ENGLISH { "Failed to release engine scope: $e" }
                Locale.KOREAN  { "엔진 스코프를 release 하지 못했습니다: $e" }
            }
        }
    }

    override fun callFunction(
        scope: Any,
        functionName: String,
        args: Array<out Any>,
        onError: (e: Exception) -> Unit
    ) {
        try {
            val rhino = scope as Scriptable
            val context = enterContext()
            val function = rhino.get(functionName, scope)
            if (function == Scriptable.NOT_FOUND || function !is Function) {
                LOG.verboseTranslated {
                    Locale.ENGLISH { "WARN: Unable to locate function: $functionName" }
                    Locale.KOREAN  { "경고: 일치하는 함수를 찾을 수 없음: $functionName" }
                }
                return
            }
            context.errorReporter = defaultErrorReporter
            function.call(context, scope, scope, args)
        } catch (e: Exception) {
            onError(e)
        }
        //ScriptableObject.callMethod(engine as Scriptable, methodName, args)
        //val invocable = engine as Invocable
        //invocable.invokeFunction(methodName, args)
    }

    override fun eval(code: String): Any {
        val context = enterContext()
        val scope = context.initStandardObjects(ImporterTopLevel(context))
        val result = context.evaluateString(scope, code, "eval", 0, null)
        Context.exit()
        return result
        //val engine = ScriptEngineManager().getEngineByName("rhino")!!
        //return engine.eval(code)
    }

    private fun indexToVersion(index: Int): Int {
        return when(index) {
            0 -> Context.VERSION_1_0
            1 -> Context.VERSION_1_1
            2 -> Context.VERSION_1_2
            3 -> Context.VERSION_1_3
            4 -> Context.VERSION_1_4
            5 -> Context.VERSION_1_5
            6 -> Context.VERSION_1_6
            7 -> Context.VERSION_1_7
            8 -> Context.VERSION_1_8
            9 -> Context.VERSION_ES6
            10 -> Context.VERSION_DEFAULT
            else -> LANG_DEF_VERSION
        }
    }

    private fun initRequire(context: Context, scope: Scriptable, sandboxed: Boolean, project: Project?): Require {
        fun parseUri(path: String): URI =
            Uri.parse("file://${path}/").toURI()

        val requirePath: MutableList<File> = arrayListOf()
        if (GlobalConfig.category("project").getBoolean("load_global_libraries", false) || isNoobMode)
            getStarLightDirectory()
                .resolve("modules")
                .let(requirePath::add)

        project?.directory?.let(requirePath::add)

        val scriptProvider = SoftCachingModuleScriptProvider(
            NestedModuleSourceProvider(
                requirePath
                    .map(File::getPath)
                    .map(::parseUri),
                null
            )
        )

        return RequireBuilder()
            .setModuleScriptProvider(scriptProvider)
            .setSandboxed(sandboxed)
            .createRequire(context, scope)
    }

    companion object {

        private const val T = "JS-라이노"

        private const val CONF_OPTIMIZE_CODE = "optimize_code"
        private const val CONF_OPTIMIZATION_LEVEL = "optimization_level"
        private const val CONF_LANG_VERSION = "js_version"
        private const val LANG_DEF_VERSION = Context.VERSION_ES6

        private val defaultErrorReporter = object : ErrorReporter {
            override fun warning(
                message: String?,
                sourceName: String?,
                line: Int,
                lineSource: String?,
                lineOffset: Int
            ) {
                LOG.warn { "$sourceName#$line: $message" }
            }

            override fun error(
                message: String?,
                sourceName: String?,
                line: Int,
                lineSource: String?,
                lineOffset: Int
            ) {
                LOG.error { "$sourceName#$line: $message" }
            }

            override fun runtimeError(
                message: String?,
                sourceName: String?,
                line: Int,
                lineSource: String?,
                lineOffset: Int
            ): EvaluatorException {
                LOG.error { "runtime $sourceName#$line: $message" }
                return EvaluatorException(message, sourceName, line, lineSource, lineOffset)
            }
        }

        private val configs = config {
            category {
                id = "JS_RHINO"
                title = T
                textColor = color { "#FFC069" }
                items {
                    toggle {
                        id = CONF_OPTIMIZE_CODE
                        title = "코드 최적화"
                        description = "코드를 컴파일 과정에서 최적화합니다. 컴파일 속도가 느려지지만 실행 속도가 빨라집니다."
                        defaultValue = false
                        icon = Icon.CHECK
                    }
                    seekbar {
                        id = CONF_OPTIMIZATION_LEVEL
                        title = "최적화 레벨"
                        dependency = CONF_OPTIMIZE_CODE
                        min = 1
                        max = 9
                        icon = Icon.COMPRESS
                        iconTintColor = color { "#57837B" }
                        defaultValue = 1
                    }
                    toggle {
                        id = "load_ext_modules"
                        title = "외부 모듈 로드"
                        description = "컴파일 시 프로젝트 폴더 내의 모듈을 로드합니다."
                        defaultValue = true
                        icon = Icon.FOLDER
                        iconTintColor = color { "#C7B198" }
                    }
                    toggle {
                        id = "load_ext_module_sandbox"
                        dependency = "load_ext_modules"
                        title = "샌드박스 환경에서 로드"
                        description = "/modules 폴더 내의 모듈을 샌드박스 환경에서 실행합니다."
                        defaultValue = true
                        icon = Icon.LOCK
                        iconTintColor = color { "#F8B400" }
                    }
                    spinner {
                        id = CONF_LANG_VERSION
                        title = "JS 버전"
                        items = listOf(
                            "JavaScript 1.0",
                            "JavaScript 1.1",
                            "JavaScript 1.2",
                            "JavaScript 1.3",
                            "JavaScript 1.4",
                            "JavaScript 1.5",
                            "JavaScript 1.6",
                            "JavaScript 1.7",
                            "JavaScript 1.8",
                            "ECMAScript 6 (ES6)",
                            "DEFAULT",
                        )
                        icon = Icon.LAYERS
                        iconTintColor = color { "#C6B4CE" }
                        defaultIndex = 9
                    }
                }
            }
        }
    }
}