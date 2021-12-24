package com.mooner.starlight.plugincore.config

import android.text.InputType
import android.view.View
import androidx.annotation.ColorInt
import com.mooner.starlight.plugincore.utils.Icon
import java.io.File

enum class ConfigObjectType(
    val viewType: Int
) {
    TOGGLE(0),
    SEEKBAR(1),
    STRING(2),
    PASSWORD(-2),
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
    val icon: Icon?
    val iconFile: File?
    val iconResId: Int?
    val iconTintColor: Int?
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
    override val icon: Icon? = null,
    override val iconFile: File? = null,
    override val iconResId: Int? = null,
    @ColorInt
    override val iconTintColor: Int?,
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

data class SeekbarConfigObject(
    override val id: String,
    override val title: String,
    override val description: String? = null,
    val max: Int,
    val min: Int = 0,
    override val default: Int,
    override val icon: Icon? = null,
    override val iconFile: File? = null,
    override val iconResId: Int? = null,
    @ColorInt
    override val iconTintColor: Int?,
    override val dependency: String? = null
): ConfigObject {
    override val type = ConfigObjectType.SEEKBAR
    override val viewType = type.viewType
}

data class StringConfigObject(
    override val id: String,
    override val title: String,
    override val description: String? = null,
    val hint: String,
    val defaultValue: String? = null,
    val inputType: Int = InputType.TYPE_CLASS_TEXT,
    val require: (String) -> String? = { null },
    override val icon: Icon? = null,
    override val iconFile: File? = null,
    override val iconResId: Int? = null,
    @ColorInt
    override val iconTintColor: Int?,
    override val dependency: String? = null
): ConfigObject {
    override val default: Any
        get() = defaultValue?: ""
    override val type: ConfigObjectType
        get() = ConfigObjectType.STRING
    override val viewType: Int
        get() = type.viewType
}

data class PasswordConfigObject(
    override val id: String,
    override val title: String,
    override val description: String? = null,
    val require: (value: String) -> String? = { null },
    val hashCode: (value: String) -> String,
    override val icon: Icon? = null,
    override val iconFile: File? = null,
    override val iconResId: Int? = null,
    @ColorInt
    override val iconTintColor: Int?,
    override val dependency: String? = null
): ConfigObject {
    override val default: Any = 0
    override val type: ConfigObjectType = ConfigObjectType.PASSWORD
    override val viewType: Int
        get() = type.viewType
}

data class SpinnerConfigObject(
    override val id: String,
    override val title: String,
    override val description: String? = null,
    val items: List<String>,
    private val defaultIndex: Int = 0,
    override val icon: Icon? = null,
    override val iconFile: File? = null,
    override val iconResId: Int? = null,
    @ColorInt
    override val iconTintColor: Int?,
    override val dependency: String? = null
): ConfigObject {
    override val default: Any
        get() = defaultIndex
    override val type: ConfigObjectType = ConfigObjectType.SPINNER
    override val viewType: Int
        get() = type.viewType
}

data class ButtonConfigObject(
    override val id: String,
    override val title: String,
    override val description: String? = null,
    val onClickListener: (view: View) -> Unit,
    private val buttonType: Type = Type.FLAT,
    override val icon: Icon? = null,
    override val iconFile: File? = null,
    override val iconResId: Int? = null,
    @ColorInt
    override val iconTintColor: Int?,
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
    override val icon: Icon? = null
    override val iconFile: File? = null
    override val iconResId: Int? = null
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
    val isDevModeOnly: Boolean,
    @ColorInt
    val textColor: Int,
    val items: List<ConfigObject>
): ConfigObject {
    override val default: Any = 0
    override val description: String? = null
    override val icon: Icon? = null
    override val iconFile: File? = null
    override val iconResId: Int? = null
    override val iconTintColor: Int = 0x0
    override val type: ConfigObjectType = ConfigObjectType.CATEGORY
    override val viewType: Int = type.viewType
    override val dependency: String? = null
}