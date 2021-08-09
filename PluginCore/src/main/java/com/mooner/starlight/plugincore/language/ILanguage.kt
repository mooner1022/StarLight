package com.mooner.starlight.plugincore.language

import android.view.View
import android.widget.ImageView

interface ILanguage {

    val id: String

    val name: String

    val fileExtension: String

    val loadIcon: (ImageView) -> Unit

    val requireRelease: Boolean

    val configObjectList: List<ConfigObject>

    val defaultCode: String

    fun onConfigUpdated(updated: Map<String, Any>) {}

    fun onConfigChanged(id: String, view: View, data: Any) {}

    fun compile(code: String, methods: List<MethodBlock>): Any

    fun release(engine: Any) {}

    fun callFunction(engine: Any, methodName: String, args: Array<Any>)

    fun eval(code: String): Any
}