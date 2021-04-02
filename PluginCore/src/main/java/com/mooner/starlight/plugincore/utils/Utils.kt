package com.mooner.starlight.plugincore.utils

import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Object
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.stream.Collectors

class Utils {
    companion object {
        fun InputStream.readString(): String {
            return BufferedReader(InputStreamReader(this))
                .lines()
                .parallel()
                .collect(Collectors.joining("\n"))
        }

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