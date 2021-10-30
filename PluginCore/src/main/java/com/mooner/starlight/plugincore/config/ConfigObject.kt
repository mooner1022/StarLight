package com.mooner.starlight.plugincore.config

import android.text.InputType
import android.view.View
import androidx.annotation.ColorInt
import com.mooner.starlight.plugincore.utils.Icon

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
    CATEGORY(6)
}

interface ConfigObject {
    val id: String
    val title: String
    val description: String?
    val icon: Icon
    val iconTintColor: Int
    val default: Any
    val type: ConfigObjectType
    val viewType: Int
    val dependency: String?
}

data class ToggleConfigObject(
    override val id: String,
    override val title: String,
    override val description: String?,
    private val defaultValue: Boolean,
    override val icon: Icon,
    @ColorInt
    override val iconTintColor: Int,
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

data class SliderConfigObject(
    override val id: String,
    override val title: String,
    override val description: String? = null,
    val max: Int,
    private val defaultValue: Int,
    override val icon: Icon,
    @ColorInt
    override val iconTintColor: Int,
    override val dependency: String? = null
): ConfigObject {
    override val default: Any
        get() = defaultValue
    override val type: ConfigObjectType
        get() = ConfigObjectType.SLIDER
    override val viewType: Int
        get() = type.viewType
}

data class StringConfigObject(
    override val id: String,
    override val title: String,
    override val description: String? = null,
    val hint: String,
    private val defaultValue: String = "",
    val inputType: Int = InputType.TYPE_CLASS_TEXT,
    val require: (String) -> String? = { null },
    override val icon: Icon,
    @ColorInt
    override val iconTintColor: Int,
    override val dependency: String? = null
): ConfigObject {
    override val default: Any
        get() = defaultValue
    override val type: ConfigObjectType
        get() = ConfigObjectType.STRING
    override val viewType: Int
        get() = type.viewType
}

data class SpinnerConfigObject(
    override val id: String,
    override val title: String,
    override val description: String? = null,
    val items: List<String>,
    private val defaultIndex: Int = 0,
    override val icon: Icon,
    @ColorInt
    override val iconTintColor: Int,
    override val dependency: String? = null
): ConfigObject {
    override val default: Any
        get() = defaultIndex
    override val type: ConfigObjectType
        get() = ConfigObjectType.SPINNER
    override val viewType: Int
        get() = type.viewType
}

data class ButtonConfigObject(
    override val id: String,
    override val title: String,
    override val description: String? = null,
    val onClickListener: (view: View) -> Unit,
    private val buttonType: Type = Type.FLAT,
    override val icon: Icon,
    @ColorInt
    override val iconTintColor: Int,
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

data class CustomConfigObject(
    override val id: String,
    val onInflate: (view: View) -> Unit,
): ConfigObject {
    override val default: Any
        get() = 0
    override val title: String = ""
    override val description: String? = null
    override val icon: Icon = Icon.ERROR
    override val iconTintColor: Int = 0x0
    override val type: ConfigObjectType
        get() = ConfigObjectType.CUSTOM
    override val viewType: Int
        get() = type.viewType
    override val dependency: String? = null
}

data class CategoryConfigObject(
    override val id: String,
    override val title: String,
    @ColorInt
    val textColor: Int,
    val items: List<ConfigObject>
): ConfigObject {
    override val default: Any = 0
    override val description: String? = null
    override val icon: Icon = Icon.ERROR
    override val iconTintColor: Int = 0x0
    override val type: ConfigObjectType = ConfigObjectType.CATEGORY
    override val viewType: Int = type.viewType
    override val dependency: String? = null
}