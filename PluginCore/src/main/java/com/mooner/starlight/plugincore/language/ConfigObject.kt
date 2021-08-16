package com.mooner.starlight.plugincore.language

import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

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
    val viewType: Int
    val dependency: String?
}

class ToggleConfigObject(
    override val id: String,
    override val name: String,
    private val defaultValue: Boolean,
    override val dependency: String? = null
): ConfigObject {
    val listeners: ArrayList<(isEnabled: Boolean) -> Unit> = arrayListOf()
    override val default: Any
        get() = defaultValue
    override val type: ConfigObjectType
        get() = ConfigObjectType.TOGGLE
    override val viewType: Int
        get() = ConfigObjectType.TOGGLE.viewType
}

class SliderConfigObject(
    override val id: String,
    override val name: String,
    val max: Int,
    private val defaultValue: Int,
    override val dependency: String? = null
): ConfigObject {
    override val default: Any
        get() = defaultValue
    override val type: ConfigObjectType
        get() = ConfigObjectType.SLIDER
    override val viewType: Int
        get() = ConfigObjectType.SLIDER.viewType
}

class StringConfigObject(
    override val id: String,
    override val name: String,
    val hint: String,
    private val defaultValue: String = "",
    val inputType: Int = InputType.TYPE_CLASS_TEXT,
    val require: (String) -> String? = { null },
    override val dependency: String? = null
): ConfigObject {
    override val default: Any
        get() = defaultValue
    override val type: ConfigObjectType
        get() = ConfigObjectType.STRING
    override val viewType: Int
        get() = ConfigObjectType.STRING.viewType
}

class SpinnerConfigObject(
    override val id: String,
    override val name: String,
    val spinnerItems: List<String>,
    private val defaultIndex: Int = 0,
    override val dependency: String? = null
): ConfigObject {
    override val default: Any
        get() = defaultIndex
    override val type: ConfigObjectType
        get() = ConfigObjectType.SPINNER
    override val viewType: Int
        get() = ConfigObjectType.SPINNER.viewType
}

class ButtonConfigObject(
    override val id: String,
    override val name: String,
    val onClickListener: () -> Unit,
    @DrawableRes
    internal val iconRes: Int? = null,
    val iconDrawable: Drawable? = null,
    val loadIcon: ((ImageView) -> Unit)? = null,
    @ColorInt
    val backgroundColorInt: Int? = null,
    override val dependency: String? = null
): ConfigObject {
    override val default: Any
        get() = 0
    override val type: ConfigObjectType
        get() = ConfigObjectType.BUTTON
    override val viewType: Int
        get() = ConfigObjectType.BUTTON.viewType
}

class CustomConfigObject(
    override val id: String,
    val onInflate: (view: View) -> Unit,
): ConfigObject {
    override val default: Any
        get() = 0
    override val name: String = ""
    override val type: ConfigObjectType
        get() = ConfigObjectType.CUSTOM
    override val viewType: Int
        get() = ConfigObjectType.CUSTOM.viewType
    override val dependency: String? = null
}