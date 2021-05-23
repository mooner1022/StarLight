package com.mooner.starlight.plugincore.language

import android.graphics.drawable.Drawable

interface ILanguage {

    val id: String

    val name: String

    val fileExtension: String

    val icon: Drawable?

    val requireRelease: Boolean

    val configObjectList: List<ConfigObject>

    val defaultCode: String

    fun onConfigChanged(changed: Map<String, Any>)

    fun compile(code: String, methods: Array<MethodBlock>): Any

    fun release(engine: Any) {}

    fun callFunction(engine: Any, methodName: String, args: Array<Any>)

    fun eval(code: String): Any
}