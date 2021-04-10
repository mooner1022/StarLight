package com.mooner.starlight.compiler

class Compiler {
    companion object {
        /*
        fun compile(lang: Languages, name: String, code: String, replier: Replier): Any {
            println("compiling project $name, code: $code")
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
                            replier,
                            arrayOf("reply", "replyTo"),
                            arrayOf(arrayOf(String::class.java), arrayOf(String::class.java, String::class.java))
                        )
                        addClass(
                                "logger",
                                getLogger(),
                                arrayOf("f", "i", "e", "w"),
                                arrayOf(
                                        arrayOf(String::class.java, String::class.java),
                                        arrayOf(String::class.java, String::class.java),
                                        arrayOf(String::class.java, String::class.java),
                                        arrayOf(String::class.java, String::class.java)
                                )
                        )
                        executeScript(code)
                    }
                }
                else -> {
                    ""
                }
            }
        }
         */
    }
}