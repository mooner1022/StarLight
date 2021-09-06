package com.mooner.starlight.plugincore.language

import android.view.View
import android.widget.ImageView
import com.mooner.starlight.plugincore.config.ConfigObject
import com.mooner.starlight.plugincore.method.MethodClass

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

    fun compile(code: String, methods: List<MethodClass>): Any

    fun release(engine: Any) {}

    fun callFunction(engine: Any, functionName: String, args: Array<Any>, onError: (e: Exception) -> Unit = {})

    fun eval(code: String): Any
}