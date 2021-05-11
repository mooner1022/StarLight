package com.mooner.starlight.plugincore.language

import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.LayoutRes

enum class ConfigObjectType(
    val viewType: Int
) {
    TOGGLE(0),
    SLIDER(1),
    STRING(2),
    SPINNER(3),
    BUTTON(4),
    CUSTOM(5)
}

interface ConfigObject {
    val id: String
    val name: String
    val default: Any
    val type: ConfigObjectType
    val viewType : Int
}

class ToggleConfigObject(
        private val objectId: String,
        private val objectName: String,
        private val defaultValue: Boolean
): ConfigObject {
    override val id: String
        get() = objectId
    override val name: String
        get() = objectName
    override val default: Any
        get() = false
    override val type: ConfigObjectType
        get() = ConfigObjectType.TOGGLE
    override val viewType: Int
        get() = ConfigObjectType.TOGGLE.viewType
}

class SliderConfigObject(
        private val objectId: String,
        private val objectName: String,
        val max: Int,
        private val defaultValue: Int
): ConfigObject {
    override val id: String
        get() = objectId
    override val name: String
        get() = objectName
    override val default: Any
        get() = defaultValue
    override val type: ConfigObjectType
        get() = ConfigObjectType.SLIDER
    override val viewType: Int
        get() = ConfigObjectType.SLIDER.viewType
}

class StringConfigObject(
        private val objectId: String,
        private val objectName: String,
        private val hint: String
): ConfigObject {
    override val id: String
        get() = objectId
    override val name: String
        get() = objectName
    override val default: Any
        get() = hint
    override val type: ConfigObjectType
        get() = ConfigObjectType.STRING
    override val viewType: Int
        get() = ConfigObjectType.STRING.viewType
}

class SpinnerConfigObject(
    private val objectId: String,
    private val objectName: String,
    val dataList: List<String>,
    private val defaultIndex: Int = 0
): ConfigObject {
    override val id: String
        get() = objectId
    override val name: String
        get() = objectName
    override val default: Any
        get() = defaultIndex
    override val type: ConfigObjectType
        get() = ConfigObjectType.SPINNER
    override val viewType: Int
        get() = ConfigObjectType.SPINNER.viewType
}

class ButtonConfigObject(
        private val objectId: String,
        private val objectName: String,
        val onClickListener: () -> Unit,
        val iconRes: Int? = null,
        val iconDrawable: Drawable? = null
): ConfigObject {
    override val id: String
        get() = objectId
    override val name: String
        get() = objectName
    override val default: Any
        get() = 0
    override val type: ConfigObjectType
        get() = ConfigObjectType.BUTTON
    override val viewType: Int
        get() = ConfigObjectType.BUTTON.viewType
}

class CustomConfigObject(
    private val objectId: String,
    private val objectName: String,
    @LayoutRes
    val layoutID: Int,
    val onInflate: (view: View) -> Unit,
): ConfigObject {
    override val id: String
        get() = objectId
    override val name: String
        get() = objectName
    override val default: Any
        get() = 0
    override val type: ConfigObjectType
        get() = ConfigObjectType.CUSTOM
    override val viewType: Int
        get() = ConfigObjectType.CUSTOM.viewType
}