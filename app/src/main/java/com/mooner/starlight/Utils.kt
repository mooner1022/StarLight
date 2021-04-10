package com.mooner.starlight

import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Object
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.language.Method
import com.mooner.starlight.plugincore.language.MethodBlock
import com.mooner.starlight.plugincore.logger.Logger

class Utils {
    companion object {
        fun getLogger(): Logger = Session.getLogger()

        fun V8.addClass(name: String, clazz: Any, methods: Array<String>, args: Array<Array<Class<*>>>) {
            val obj = V8Object(this)
            this.add(name, obj)

            for ((i, method) in methods.withIndex()) {
                obj.registerJavaMethod(
                    clazz,
                    method,
                    method,
                    args[i]
                )
            }
            obj.close()
        }
    }
}