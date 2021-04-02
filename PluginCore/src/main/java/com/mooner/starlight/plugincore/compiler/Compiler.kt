package com.mooner.starlight.plugincore.compiler

import com.eclipsesource.v8.V8
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.language.Languages
import com.mooner.starlight.plugincore.project.ProjectLoader
import com.mooner.starlight.plugincore.utils.Utils.Companion.addClass
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory

class Compiler {
    companion object {
        fun compile(lang: Languages, name: String, code: String): Any {
            return when(lang) {
                Languages.JS_RHINO -> {
                    val factory = ContextFactory.getGlobal()
                    val context = factory.enterContext().apply {
                        optimizationLevel = -1
                        languageVersion = Context.VERSION_ES6
                    }
                    val shared = context.initStandardObjects()
                    val scope = context.newObject(shared)
                    context.evaluateString(scope, code, name, 1, null)
                    scope
                }
                Languages.JS_V8 -> {
                    V8.createV8Runtime().apply {
                        addClass(
                            "replier",
                            Session,
                            arrayOf("reply","reply"),
                            arrayOf(arrayOf(String::class.java), arrayOf(String::class.java, String::class.java))
                        )
                    }
                }
                else -> {
                    ""
                }
            }
        }
    }
}