package com.mooner.starlight.plugincore.language

import android.graphics.drawable.Drawable

interface Language {

    val id: String

    val name: String

    val icon: Drawable?

    val requireRelease: Boolean

    val configList: List<LanguageConfig>

    fun compile(code: String, methods: Array<MethodBlock>): Any

    fun release(engine: Any) {}

    fun execute(engine: Any, methodName: String, args: Array<Any>)
}