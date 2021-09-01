package com.mooner.starlight.plugincore.config

import android.text.InputType
import android.view.View
import androidx.annotation.ColorInt
import com.mooner.starlight.plugincore.utils.Icon
import java.util.*

enum class ConfigObjectType(
    val viewType: Int
) {
    TOGGLE(0),
    SLIDER(1),
    STRING(2),
    SPINNER(3),
    BUTTON_FLAT(4),
    BUTTON_CARD(-4),
    CUSTOM(5),
    TITLE(6)
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
        get() = type.viewType
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
        get() = type.viewType
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
        get() = type.viewType
}

class SpinnerConfigObject(
    override val id: String,
    override val name: String,
    val items: List<String>,
    private val defaultIndex: Int = 0,
    override val dependency: String? = null
): ConfigObject {
    override val default: Any
        get() = defaultIndex
    override val type: ConfigObjectType
        get() = ConfigObjectType.SPINNER
    override val viewType: Int
        get() = type.viewType
}

class ButtonConfigObject(
    override val id: String,
    override val name: String,
    val onClickListener: (view: View) -> Unit,
    private val buttonType: Type = Type.FLAT,
    val icon: Icon = Icon.ARROW_RIGHT,
    @ColorInt
    val iconTintColor: Int? = null,
    @ColorInt
    val backgroundColor: Int? = null,
    override val dependency: String? = null
): ConfigObject {
    override val default: Any
        get() = 0
    override val type: ConfigObjectType
        get() = when(buttonType) {
            Type.FLAT -> ConfigObjectType.BUTTON_FLAT
            Type.CARD -> ConfigObjectType.BUTTON_CARD
        }
    override val viewType: Int
        get() = type.viewType

    enum class Type {
        FLAT,
        CARD
    }
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
        get() = type.viewType
    override val dependency: String? = null
}

class TitleConfigObject(
    val title: String,
    @ColorInt
    val textColor: Int
): ConfigObject {
    override val default: Any = 0
    override val id: String = UUID.randomUUID().toString()
    override val name: String = title
    override val type: ConfigObjectType = ConfigObjectType.TITLE
    override val viewType: Int
        get() = type.viewType
    override val dependency: String? = null
}