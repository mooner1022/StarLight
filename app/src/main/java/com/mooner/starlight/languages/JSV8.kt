package com.mooner.starlight.languages

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.eclipsesource.v8.V8
import com.mooner.starlight.R
import com.mooner.starlight.Utils.Companion.addClass
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.plugincore.language.*

class JSV8: Language {
    override val id: String
        get() = "JS_V8"
    override val name: String
        get() = "자바스크립트 (V8)"
    override val icon: Drawable
        get() = ContextCompat.getDrawable(ApplicationSession.context, R.drawable.ic_nodejs)!!
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
            )
        )

    override fun compile(code: String, methods: Array<MethodBlock>): Any {
        val v8 = V8.createV8Runtime()
        v8.apply {
            for (methodBlock in methods) {
                addClass(
                    methodBlock.blockName,
                    methodBlock.methodClass,
                    methodBlock.methods.map { it.methodName }.toTypedArray(),
                    methodBlock.methods.map { it.args }.toTypedArray()
                )
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
}