package com.mooner.starlight.languages

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.language.MethodBlock
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

class JSRhino: Language {
    override val id: String
        get() = "JS_RHINO"
    override val name: String
        get() = "자바스크립트 (라이노)"
    override val icon: Drawable
        get() = ContextCompat.getDrawable(ApplicationSession.context, R.drawable.ic_js)!!
    override val requireRelease: Boolean
        get() = false

    override fun compile(code: String, methods: Array<MethodBlock>): Any {
        val factory = ContextFactory.getGlobal()
        val context = factory.enterContext().apply {
            optimizationLevel = -1
            languageVersion = Context.VERSION_ES6
        }
        val shared = context.initStandardObjects()
        val scope = context.newObject(shared)
        context.evaluateString(scope, code, name, 1, null)
        //scope.put()
        return scope
    }

    override fun execute(engine: Any, methodName: String, args: Array<Any>) {
        ScriptableObject.callMethod(engine as Scriptable, methodName, args)
    }
}