package com.mooner.starlight.plugincore.language

import android.graphics.drawable.Drawable
import androidx.annotation.FloatRange

enum class LanguageConfigType(
    val viewType: Int
) {
    TOGGLE(0),
    SLIDER(1),
    STRING(2),
    SPINNER(3),
    BUTTON(4)
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
        get() = LanguageConfigType.TOGGLE.viewType
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
        get() = LanguageConfigType.SLIDER.viewType
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
        get() = LanguageConfigType.STRING.viewType
}

class SpinnerLanguageConfig(
    private val objectId: String,
    private val objectName: String,
    val dataList: List<String>,
    private val defaultIndex: Int = 0
): LanguageConfig {
    override val id: String
        get() = objectId
    override val name: String
        get() = objectName
    override val default: Any
        get() = defaultIndex
    override val type: LanguageConfigType
        get() = LanguageConfigType.SPINNER
    override val viewType: Int
        get() = LanguageConfigType.SPINNER.viewType
}

class ButtonLanguageConfig(
        private val objectId: String,
        private val objectName: String,
        val onClickListener: () -> Unit,
        val iconRes: Int? = null,
        val iconDrawable: Drawable? = null
): LanguageConfig {
    override val id: String
        get() = objectId
    override val name: String
        get() = objectName
    override val default: Any
        get() = 0
    override val type: LanguageConfigType
        get() = LanguageConfigType.BUTTON
    override val viewType: Int
        get() = LanguageConfigType.BUTTON.viewType
}