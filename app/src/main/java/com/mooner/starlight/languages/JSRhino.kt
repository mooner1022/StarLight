package com.mooner.starlight.languages

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.language.*
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import javax.script.Invocable
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

class JSRhino: Language() {
    companion object {
        private const val CONF_OPTIMIZATION = "optimizationLevel"
    }

    override val id: String
        get() = "JS_RHINO"
    override val name: String
        get() = "자바스크립트 (라이노)"
    override val fileExtension: String
        get() = "js"
    override val icon: Drawable
        get() = ContextCompat.getDrawable(ApplicationSession.context, R.drawable.ic_js)!!
    override val requireRelease: Boolean
        get() = false

    override val configList: List<LanguageConfig>
        get() = listOf(
            SliderLanguageConfig(
                objectId = CONF_OPTIMIZATION,
                objectName = "최적화 레벨",
                max = 10,
                defaultValue = 1
            )
        )

    override fun onConfigChanged(changed: Map<String, Any>) {

    }

    override fun compile(code: String, methods: Array<MethodBlock>): Any {
        val engine = ScriptEngineManager().getEngineByName("rhino")!!
        engine.eval(code)
        /*
        val factory = ContextFactory.getGlobal()
        val context = factory.enterContext().apply {
            optimizationLevel = -1
            languageVersion = Context.VERSION_ES6
        }
        val shared = context.initStandardObjects()
        val scope = context.newObject(shared)
        context.evaluateString(scope, code, name, 1, null)
        */
        for(methodBlock in methods) {
            //ScriptableObject.defineClass(scope, )
            engine.put(methodBlock.blockName, methodBlock.instance)
        }
        //scope.put()
        return engine
    }

    override fun execute(engine: Any, methodName: String, args: Array<Any>) {
        //ScriptableObject.callMethod(engine as Scriptable, methodName, args)
        val invocable = engine as Invocable
        invocable.invokeFunction(methodName, args)
    }

    override fun eval(code: String): Any {
        val engine = ScriptEngineManager().getEngineByName("rhino")!!
        return engine.eval(code)
    }
}