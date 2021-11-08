package com.mooner.starlight.plugincore.config

import android.graphics.Color
import android.text.InputType
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.mooner.starlight.plugincore.utils.Icon
import java.io.File

fun config(block: ConfigBuilder.() -> Unit): List<CategoryConfigObject> {
    val builder = ConfigBuilder().apply(block)
    return builder.build(flush = true)
}

class ConfigBuilder {

    private val objects: MutableList<CategoryConfigObject> = arrayListOf()

    inline fun color(color: () -> String): Int = Color.parseColor(color())

    fun category(block: CategoryConfigBuilder.() -> Unit) {
        val category = CategoryConfigBuilder().apply(block)
        objects.add(category.build())
    }

    fun build(flush: Boolean = true): List<CategoryConfigObject> {
        val list = objects.toList()
        if (flush) {
            objects.clear()
        }
        return list
    }

    fun items(block: ConfigItemBuilder.() -> Unit): List<ConfigObject> {
        val builder = ConfigItemBuilder().apply(block)
        return builder.build(flush = true)
    }

    private fun required(fieldName: String, value: Any?) {
        if (value == null) {
            throw IllegalArgumentException("Required field '$fieldName' is null")
        }
    }

    inner class CategoryConfigBuilder {
        var id: String? = null
        var title: String? = null
        @ColorInt
        var textColor: Int = Color.parseColor("#000000")
        var items: List<ConfigObject> = arrayListOf()

        fun build(): CategoryConfigObject {
            required("id", id)
            //required("title", title)
            require(items.isNotEmpty()) { "Field 'items' must not be empty" }

            return CategoryConfigObject(
                id = id!!,
                title = title?: "",
                textColor = textColor,
                items = items
            )
        }
    }
}

class ConfigItemBuilder {
    private val objects: MutableList<ConfigObject> = arrayListOf()

    inline fun color(color: () -> String): Int = Color.parseColor(color())

    fun button(block: ButtonConfigBuilder.() -> Unit) {
        val button = ButtonConfigBuilder().apply(block)
        objects.add(button.build())
    }

    fun toggle(block: ToggleConfigBuilder.() -> Unit) {
        val toggle = ToggleConfigBuilder().apply(block)
        objects.add(toggle.build())
    }

    fun slider(block: SliderConfigBuilder.() -> Unit) {
        val slider = SliderConfigBuilder().apply(block)
        objects.add(slider.build())
    }

    fun string(block: StringConfigBuilder.() -> Unit) {
        val string = StringConfigBuilder().apply(block)
        objects.add(string.build())
    }

    fun spinner(block: SpinnerConfigBuilder.() -> Unit) {
        val spinner = SpinnerConfigBuilder().apply(block)
        objects.add(spinner.build())
    }

    fun custom(block: CustomConfigBuilder.() -> Unit) {
        val custom = CustomConfigBuilder().apply(block)
        objects.add(custom.build())
    }

    fun build(flush: Boolean = true): List<ConfigObject> {
        val list = objects.toList()
        if (flush) {
            objects.clear()
        }
        return list
    }

    private fun required(fieldName: String, value: Any?) {
        if (value == null) {
            throw IllegalArgumentException("Required field '$fieldName' is null")
        }
    }

    private fun required(fieldNames: String, vararg values: Any?) {
        for (value in values) {
            if (value != null)
                return
        }
        throw IllegalArgumentException("Least one of required field [$fieldNames] should not be null")
    }

    abstract inner class ConfigBuilder {
        var id: String? = null
        var title: String? = null
        var description: String? = null

        var icon: Icon? = null
        var iconFile: File? = null
        @DrawableRes
        var iconResId: Int? = null

        @ColorInt
        var iconTintColor: Int? = Color.parseColor("#000000")

        var dependency: String? = null

        fun setIcon(icon: Icon? = null, iconFile: File? = null, @DrawableRes iconResId: Int? = null) {
            when {
                icon != null -> this.icon = icon
                iconFile != null -> this.iconFile = iconFile
                iconResId != null -> this.iconResId = iconResId
            }
        }
    }

    inner class ButtonConfigBuilder: ConfigBuilder() {
        var onClickListener: ((view: View) -> Unit)? = null
        var type: ButtonConfigObject.Type = ButtonConfigObject.Type.FLAT
        @ColorInt
        var backgroundColor: Int? = null

        fun build(): ButtonConfigObject {
            required("id", id)
            required("title", title)
            required("onClickListener", onClickListener)
            required("icon, iconFile, iconResId", icon, iconFile, iconResId)

            return ButtonConfigObject(
                id = id!!,
                title = title!!,
                description = description,
                onClickListener = onClickListener!!,
                buttonType = type,
                icon = icon,
                iconFile = iconFile,
                iconResId = iconResId,
                iconTintColor = iconTintColor,
                backgroundColor = backgroundColor,
                dependency = dependency
            )
        }
    }

    inner class ToggleConfigBuilder: ConfigBuilder() {
        var defaultValue: Boolean = false

        fun build(): ToggleConfigObject {
            required("id", id)
            required("title", title)
            required("icon, iconFile, iconResId", icon, iconFile, iconResId)

            return ToggleConfigObject(
                id = id!!,
                title = title!!,
                description = description,
                defaultValue = defaultValue,
                icon = icon,
                iconFile = iconFile,
                iconResId = iconResId,
                iconTintColor = iconTintColor,
                dependency = dependency
            )
        }
    }

    inner class SliderConfigBuilder: ConfigBuilder() {
        var max: Int? = null
        var defaultValue: Int = 0

        fun build(): SliderConfigObject {
            required("id", id)
            required("title", title)
            required("max", max)
            required("icon, iconFile, iconResId", icon, iconFile, iconResId)

            return SliderConfigObject(
                id = id!!,
                title = title!!,
                description = description,
                max = max!!,
                defaultValue = defaultValue,
                icon = icon,
                iconFile = iconFile,
                iconResId = iconResId,
                iconTintColor = iconTintColor,
                dependency = dependency
            )
        }
    }

    inner class StringConfigBuilder: ConfigBuilder() {
        var hint: String? = null
        var inputType: Int = InputType.TYPE_CLASS_TEXT
        var require: (String) -> String? = { null }

        fun setIcon(icon: Icon? = null, iconFile: File? = null) {

        }

        fun build(): StringConfigObject{
            required("id", id)
            required("title", title)
            required("icon, iconFile, iconResId", icon, iconFile, iconResId)

            return StringConfigObject(
                id = id!!,
                title = title!!,
                description = description,
                hint = hint ?: "",
                inputType = inputType,
                require = require,
                icon = icon,
                iconFile = iconFile,
                iconResId = iconResId,
                iconTintColor = iconTintColor,
                dependency = dependency
            )
        }
    }

    inner class SpinnerConfigBuilder: ConfigBuilder() {
        var items: List<String>? = null
        var defaultIndex: Int = 0

        fun build(): SpinnerConfigObject {
            required("id", id)
            required("title", title)
            required("items", items)
            required("icon, iconFile, iconResId", icon, iconFile, iconResId)

            return SpinnerConfigObject(
                id = id!!,
                title = title!!,
                description = description,
                items = items!!,
                defaultIndex = defaultIndex,
                icon = icon,
                iconFile = iconFile,
                iconResId = iconResId,
                iconTintColor = iconTintColor,
                dependency = dependency
            )
        }
    }

    inner class CustomConfigBuilder {
        var id: String? = null
        var onInflate: ((view: View) -> Unit)? = null

        fun build(): CustomConfigObject {
            required("id", id)
            required("onInflate", onInflate)

            return CustomConfigObject(
                id = id!!,
                onInflate = onInflate!!
            )
        }
    }
}