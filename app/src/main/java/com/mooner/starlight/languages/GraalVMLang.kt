package com.mooner.starlight.languages

import android.widget.ImageView
import com.mooner.starlight.plugincore.language.ConfigObject
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.methods.MethodClass
import org.graalvm.polyglot.Context

class GraalVMLang: Language() {

    companion object {
        private const val T = "GraalVM"
    }

    override val configObjectList: List<ConfigObject> = listOf()
    override val defaultCode: String = """
        function onMessage(event) {
            
        }
    """.trimIndent()
    override val fileExtension: String = "js"
    override val id: String = "JS_GRAALVM"
    override val loadIcon: (ImageView) -> Unit = {

    }
    override val name: String = "자바스크립트 (GraalVM)"
    override val requireRelease: Boolean = false

    override fun callFunction(engine: Any, methodName: String, args: Array<Any>) {
        //val function = context.
        //function.executeVoid(args)
    }

    override fun compile(code: String, methods: List<MethodClass>): Any {
        val context = Context.getCurrent()
        context.eval("js", code)
        return context
    }

    override fun eval(code: String): Any {
        val context = Context.create("js")
        return context.eval("js", code).execute().asString()
    }
}