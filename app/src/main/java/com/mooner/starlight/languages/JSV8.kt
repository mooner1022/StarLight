package com.mooner.starlight.languages

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.ActionBar
import coil.load
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Object
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.language.*
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.methods.MethodClass

class JSV8: Language() {
    override val id: String
        get() = "JS_V8"
    override val name: String
        get() = "자바스크립트 (V8)"
    override val fileExtension: String
        get() = "js"
    override val loadIcon: (ImageView) -> Unit = { imageView ->
        imageView.load(R.drawable.ic_v8)
    }
    override val requireRelease: Boolean
        get() = true
    override val defaultCode: String
        get() = """
            function response(sender, message, room, imageDB, replier) {
                
            }
        """.trimIndent()

    override val configObjectList: List<ConfigObject>
        get() = listOf(
            ToggleConfigObject(
                id = "toggle_test",
                name = "토글 테스트",
                defaultValue = false
            ),
            SliderConfigObject(
                id = "slider_test",
                name = "슬라이더 테스트",
                max = 5,
                defaultValue = 2
            ),
            StringConfigObject(
                id = "string_test",
                name = "인풋 테스트",
                hint = "테스트으으"
            ),
            SpinnerConfigObject(
                id = "spinner_test",
                name = "스피너 테스트",
                spinnerItems = listOf(
                    "테스트1",
                    "테스트2",
                    "테스트3",
                    "짺스"
                )
            ),
            ButtonConfigObject(
                id = "button_test",
                name = "버튼 테스트",
                onClickListener = {
                    Logger.d("JSV8_Config", "onClickListener")
                },
                iconRes = R.drawable.ic_round_keyboard_arrow_right_24,
                backgroundColor = Color.parseColor("#ffa361")
            ),
            CustomConfigObject(
                id = "custom_test",
                onInflate = {
                    val imageView = ImageView(it.context).apply {
                        layoutParams = ActionBar.LayoutParams(1440, 1440).apply { gravity = Gravity.CENTER }
                        maxWidth = 500
                        maxHeight = 500
                        x = 0f
                        y = 0f
                    }
                    (it as LinearLayout).addView(imageView)
                    imageView.load(R.drawable.splash_anim)
                }
            )
        )

    override fun compile(code: String, methods: List<MethodClass>): Any {
        val v8 = V8.createV8Runtime()
        try {
            v8.apply {
                for (methodBlock in methods) {
                    addClass(
                        methodBlock.className,
                        methodBlock.instance,
                        methodBlock.functions.map { it.name }.toTypedArray(),
                        methodBlock.functions.map { it.args }.toTypedArray()
                    )
                }
                executeScript(code)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            v8.locker.release()
        }
        return v8
    }

    override fun release(engine: Any) {
        val v8 = engine as V8
        if (!v8.isReleased && v8.locker.hasLock()) {
            v8.release(false)
        }
    }

    override fun callFunction(engine: Any, methodName: String, args: Array<Any>) {
        val v8 = engine as V8
        v8.locker.acquire()
        v8.executeJSFunction(methodName, *args)
        v8.locker.release()
    }

    override fun eval(code: String): Any {
        val engine = compile(code, listOf()) as V8
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