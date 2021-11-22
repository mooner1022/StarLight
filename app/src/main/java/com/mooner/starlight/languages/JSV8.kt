package com.mooner.starlight.languages

import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.ActionBar
import coil.load
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.V8Value
import com.eclipsesource.v8.utils.V8ObjectUtils
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiFunction
import com.mooner.starlight.plugincore.api.ApiObject
import com.mooner.starlight.plugincore.api.ApiValue
import com.mooner.starlight.plugincore.config.CategoryConfigObject
import com.mooner.starlight.plugincore.config.config
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.models.Message
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.plugincore.utils.Icon


class JSV8: Language() {

    companion object {
        private const val T = "JS-V8"
    }

    override val id: String
        get() = "JS_V8"
    override val name: String
        get() = "자바스크립트(V8)"
    override val fileExtension: String
        get() = "js"
    override val requireRelease: Boolean
        get() = true
    override val defaultCode: String
        get() = """
            function onMessage(event) {
                
            }
        """.trimIndent()

    override val configObjectList: List<CategoryConfigObject> = config {
        category {
            id = T
            title = T
            items = items {
                toggle {
                    id = "toggle_test"
                    title = "토글 테스트"
                    defaultValue = false
                    icon = Icon.ADD
                }
                slider {
                    id = "slider_test"
                    title = "슬라이더 테스트"
                    max = 5
                    defaultValue = 2
                    icon = Icon.ADD
                    dependency = "toggle_test"
                }
                toggle {
                    id = "toggle_test2"
                    title = "토글 테스트2"
                    defaultValue = true
                    icon = Icon.ADD
                    dependency = "toggle_test"
                }
                string {
                    id = "string_test"
                    title = "인풋 테스트"
                    hint = "테스트으으"
                    icon = Icon.ADD
                }
                spinner {
                    id = "spinner_test"
                    title = "스피너 테스트"
                    items = listOf(
                        "테스트1",
                        "테스트2",
                        "테스트3"
                    )
                    icon = Icon.ADD
                }
                button {
                    id = "button_test"
                    title = "버튼 테스트"
                    onClickListener = {
                        Logger.v("JSV8_Config", "onClickListener")
                    }
                    backgroundColor = Color.parseColor("#ffa361")
                    icon = Icon.ADD
                }
                custom {
                    id = "custom_test"
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
                }
            }
        }
    }


    override fun onConfigUpdated(updated: Map<String, Any>) {
        Logger.i("JSV8", "updated: $updated")
    }

    override fun compile(code: String, apis: List<Api<Any>>, project: Project?): Any {
        val v8 = V8.createV8Runtime()
        try {
            v8.apply {
                for (api in apis) {
                    addClass(
                        api.name,
                        api.getInstance(project!!),
                        api.objects
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

    override fun callFunction(engine: Any, functionName: String, args: Array<Any>, onError: (e: Exception) -> Unit) {
        val v8 = engine as V8
        v8.locker.acquire()
        var messageArg: Message? = null

        try {
            if (args.find { it is Message }.also { if (it != null) messageArg = it as Message } != null) {
                val msgParams: V8Value = V8ObjectUtils.toV8Object(v8,
                    hashMapOf(
                        "message" to messageArg!!.message,
                        "room" to mapOf(
                            "name" to messageArg!!.room.name,
                            "isGroupChat" to messageArg!!.room.isGroupChat,
                            "send" to messageArg!!.room::send
                        ),
                        "sender" to mapOf(
                            "name" to messageArg!!.sender.name,
                            "profileBase64" to messageArg!!.sender.profileBase64,
                            "profileHash" to messageArg!!.sender.profileHash
                        ),
                        "packageName" to messageArg!!.packageName
                    )
                )
                v8.executeJSFunction(functionName, V8Array(v8).push(msgParams))
            } else {
                v8.executeJSFunction(functionName, *args)
            }
        } catch (e: Exception) {
            onError(e)
        } finally {
            v8.locker.release()
        }
    }

    override fun eval(code: String): Any {
        val engine = compile(code, listOf(), null) as V8
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

    private fun V8.addClass(name: String, clazz: Any, objects: List<ApiObject>) {
        val obj = V8Object(this)
        this.add(name, obj)

        for (aObj in objects) {
            when(aObj) {
                is ApiFunction -> {
                    obj.registerJavaMethod(
                        clazz,
                        aObj.name,
                        aObj.name,
                        aObj.args
                    )
                }
                is ApiValue -> {
                    val functionName = aObj.name.toGetterFunctionName()
                    obj.registerJavaMethod(
                        clazz,
                        functionName,
                        functionName,
                        emptyArray()
                    )
                }
            }
        }
        obj.close()
    }

    private fun String.toGetterFunctionName(): String {
        val firstWord = this.substring(0..1)
        return "get${firstWord.uppercase()}${this.drop(1)}"
    }
}