package com.mooner.starlight.plugincore.language

import androidx.annotation.FloatRange

enum class LanguageConfigType(
    val viewType: Int
) {
    TOGGLE(0),
    SLIDER(1),
    STRING(2)
}

interface LanguageConfig {
    val id: String
    val name: String
    val default: Any
    val type: LanguageConfigType
    val viewType : Int
}

class ToggleLanguageConfig(
        private val objectId: String,
        private val objectName: String,
        private val defaultValue: Boolean
): LanguageConfig {
    override val id: String
        get() = objectId
    override val name: String
        get() = objectName
    override val default: Any
        get() = false
    override val type: LanguageConfigType
        get() = LanguageConfigType.TOGGLE
    override val viewType: Int
        get() = 0
}

class SliderLanguageConfig(
        private val objectId: String,
        private val objectName: String,
        val max: Int,
        private val defaultValue: Int
): LanguageConfig {
    override val id: String
        get() = objectId
    override val name: String
        get() = objectName
    override val default: Any
        get() = defaultValue
    override val type: LanguageConfigType
        get() = LanguageConfigType.SLIDER
    override val viewType: Int
        get() = 1
}

class StringLanguageConfig(
        private val objectId: String,
        private val objectName: String,
        private val hint: String
): LanguageConfig {
    override val id: String
        get() = objectId
    override val name: String
        get() = objectName
    override val default: Any
        get() = hint
    override val type: LanguageConfigType
        get() = LanguageConfigType.STRING
    override val viewType: Int
        get() = 2
}