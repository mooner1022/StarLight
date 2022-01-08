package dev.mooner.starlight.languages

import com.faendir.rhino_android.RhinoAndroidHelper
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.config.CategoryConfigObject
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.utils.Icon
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.ImporterTopLevel
import org.mozilla.javascript.Scriptable
import java.io.File

class JSRhino: Language() {

    //private lateinit var context: Context
    companion object {

        private const val T = "JS-라이노"

        private const val CONF_OPTIMIZE_CODE = "optimize_code"
        private const val CONF_OPTIMIZATION_LEVEL = "optimization_level"
        private const val CONF_LANG_VERSION = "js_version"
        private const val LANG_DEF_VERSION = Context.VERSION_ES6

        private val configs = config {
            category {
                id = T
                title = T
                textColor = color { "#FFC069" }
                items = items {
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

    override val id: String = "JS_RHINO"

    override val name: String = "자바스크립트(라이노)"

    override val fileExtension: String = "js"

    override val requireRelease: Boolean = true

    override val configObjectList: List<CategoryConfigObject> = configs

    override val defaultCode: String = """
            function onMessage(event) {
                
            }
        """.trimIndent()

    override fun onConfigUpdated(updated: Map<String, Any>) {}

    private fun enterContext(): Context {
        val config = getLanguageConfig()
        val optLevel = if (config.getBoolean(CONF_OPTIMIZE_CODE, false))
            config.getInt(CONF_OPTIMIZATION_LEVEL, 0)
        else
            -1
        val context = RhinoAndroidHelper(File(System.getProperty("java.io.tmpdir", "."), "classes")).enterContext().apply {
            optimizationLevel = optLevel
            val langVersionIndex = config.getInt(CONF_LANG_VERSION)
            languageVersion = if (langVersionIndex == null)
                LANG_DEF_VERSION
            else
                indexToVersion(langVersionIndex)
            wrapFactory = PrimitiveWrapFactory()
        }
        return context
    }

    override fun compile(code: String, apis: List<Api<Any>>, project: Project?): Any {
        val context = enterContext()
        val scope = context.initStandardObjects(ImporterTopLevel(context))
        //val scope = context.newObject(shared)
        //val engine = ScriptEngineManager().getEngineByName("rhino")!!
        if (project != null) {
            var importLines: StringBuilder? = null
            for(methodBlock in apis) {
                val instance = methodBlock.getInstance(project)

                when(methodBlock.instanceType) {
                    InstanceType.CLASS -> {
                        val line = "const ${methodBlock.name} = ${methodBlock.instanceClass.name};\n"
                        if (importLines == null) importLines = StringBuilder(line)
                        else importLines.append(line)
                        //context.evaluateString(scope, "const ${methodBlock.name} = ${methodBlock.instanceClass.name};", "import", 1, null)
                    }
                    InstanceType.OBJECT -> {
                        scope.put(methodBlock.name, scope, instance)
                        //ScriptableObject.putProperty(scope, methodBlock.name, Context.javaToJS(instance, scope))
                    }
                }
                //engine.put(methodBlock.blockName, methodBlock.instance)
            }
            if (importLines != null) {
                context.evaluateString(scope, importLines.toString(), "import", 1, null)
            }
        }
        //engine.eval(code)
        context.evaluateString(scope, code, project?.info?.name?: name, 1, null)
        //scope.put()
        Context.exit()
        return scope
    }

    override fun release(scope: Any) {
        super.release(scope)
        try {
            Context.exit()
        } catch (e: IllegalStateException) {
            Logger.v(T, "Failed to release engine: ${e.message}")
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
                Logger.v(T, "Unable to locate function: $functionName")
                return
            }
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
}