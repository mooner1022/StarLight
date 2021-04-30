package com.mooner.starlight.languages

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.utils.V8ObjectUtils
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.Session.Companion.getLogger
import com.mooner.starlight.plugincore.language.*
import io.alicorn.v8.V8JavaAdapter

class JSV8: Language() {
    override val id: String
        get() = "JS_V8"
    override val name: String
        get() = "자바스크립트 (V8)"
    override val fileExtension: String
        get() = "js"
    override val icon: Drawable
        get() = ContextCompat.getDrawable(ApplicationSession.context, R.drawable.ic_v8)!!
    override val requireRelease: Boolean
        get() = true

    override val configList: List<LanguageConfig>
        get() = listOf(
            ToggleLanguageConfig(
                objectId = "toggle_test",
                objectName = "토글 테스트",
                defaultValue = false
            ),
            SliderLanguageConfig(
                objectId = "slider_test",
                objectName = "슬라이더 테스트",
                max = 5,
                defaultValue = 2
            ),
            StringLanguageConfig(
                objectId = "string_test",
                objectName = "인풋 테스트",
                hint = "테스트으으"
            )
        )

    override fun onConfigChanged(changed: Map<String, Any>) {
        getLogger().i("JSV8", "changed: $changed")
    }

    override fun compile(code: String, methods: Array<MethodBlock>): Any {
        val v8 = V8.createV8Runtime()
        v8.apply {
            for (methodBlock in methods) {
                if (methodBlock.isCustomClass) {
                    V8JavaAdapter.injectObject(methodBlock.blockName, methodBlock.methodClass, v8)
                    getLogger().i(javaClass.simpleName, "Injected ${methodBlock.blockName}")
                    /*
                    addCustomClass(
                        methodBlock.blockName,
                        methodBlock.methodClass,
                        methodBlock.methods.map { it.methodName }.toTypedArray(),
                        methodBlock.methods.map { it.args }.toTypedArray()
                    )
                    */
                } else {
                    addClass(
                        methodBlock.blockName,
                        methodBlock.methodClass,
                        methodBlock.methods.map { it.methodName }.toTypedArray(),
                        methodBlock.methods.map { it.args }.toTypedArray()
                    )
                }
            }
            executeScript(code)
        }
        v8.locker.release()
        return v8
    }

    override fun release(engine: Any) {
        val v8 = engine as V8
        if (!v8.isReleased && v8.locker.hasLock()) {
            v8.release(false)
        }
    }

    override fun execute(engine: Any, methodName: String, args: Array<Any>) {
        val v8 = engine as V8
        v8.locker.acquire()
        v8.executeJSFunction(methodName, *args)
        v8.locker.release()
    }

    override fun eval(code: String): Any {
        val engine = compile(code, arrayOf()) as V8
        try {
            engine.locker.acquire()
            return engine.executeScript(code)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            release(engine)
        }
    }

    private fun V8.addClass(name: String, clazz: Any, methods: Array<String>, args: Array<Array<Class<*>>>) {
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