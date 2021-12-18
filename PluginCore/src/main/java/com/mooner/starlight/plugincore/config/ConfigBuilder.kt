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

    fun devModeCategory(block: DevCategoryConfigBuilder.() -> Unit) {
        val category = DevCategoryConfigBuilder().apply(block)
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
                isDevModeOnly = false,
                textColor = textColor,
                items = items
            )
        }
    }

    inner class DevCategoryConfigBuilder {
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
                isDevModeOnly = true,
                textColor = textColor,
                items = items
            )
        }
    }
}

class ConfigItemBuilder {
    private val objects: MutableList<ConfigObject> = arrayListOf()

    inline fun color(color: () -> String): Int = Color.parseColor(color())

    private fun add(builder: ConfigBuilder) {
        objects += builder.build()
    }

    fun button(block: ButtonConfigBuilder.() -> Unit) {
        val button = ButtonConfigBuilder().apply(block)
        add(button)
    }

    fun toggle(block: ToggleConfigBuilder.() -> Unit) {
        val toggle = ToggleConfigBuilder().apply(block)
        add(toggle)
    }

    fun slider(block: SliderConfigBuilder.() -> Unit) {
        val slider = SliderConfigBuilder().apply(block)
        add(slider)
    }

    fun string(block: StringConfigBuilder.() -> Unit) {
        val string = StringConfigBuilder().apply(block)
        add(string)
    }

    fun password(block: PasswordConfigBuilder.() -> Unit) {
        val pw = PasswordConfigBuilder().apply(block)
        add(pw)
    }

    fun spinner(block: SpinnerConfigBuilder.() -> Unit) {
        val spinner = SpinnerConfigBuilder().apply(block)
        add(spinner)
    }

    fun custom(block: CustomConfigBuilder.() -> Unit) {
        val custom = CustomConfigBuilder().apply(block)
        add(custom)
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

        abstract fun build(): ConfigObject
    }

    inner class ButtonConfigBuilder: ConfigBuilder() {
        var onClickListener: ((view: View) -> Unit)? = null
        var type: ButtonConfigObject.Type = ButtonConfigObject.Type.FLAT
        @ColorInt
        var backgroundColor: Int? = null

        override fun build(): ButtonConfigObject {
            required("id", id)
            required("title", title)
            required("onClickListener", onClickListener)

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

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

        override fun build(): ToggleConfigObject {
            required("id", id)
            required("title", title)

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

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

        override fun build(): SliderConfigObject {
            required("id", id)
            required("title", title)
            required("max", max)

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

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
        var defaultValue: String? = null
        var inputType: Int = InputType.TYPE_CLASS_TEXT
        var require: (String) -> String? = { null }

        override fun build(): StringConfigObject{
            required("id", id)
            required("title", title)

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

            return StringConfigObject(
                id = id!!,
                title = title!!,
                description = description,
                hint = hint ?: "",
                defaultValue = defaultValue,
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

    inner class PasswordConfigBuilder: ConfigBuilder() {
        var require: (value: String) -> String? = { null }
        var hashCode: ((value: String) -> String)? = null

        override fun build(): PasswordConfigObject{
            required("id", id)
            required("title", title)
            required("hashCode", hashCode)

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

            return PasswordConfigObject(
                id = id!!,
                title = title!!,
                description = description,
                require = require,
                hashCode = hashCode!!,
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

        override fun build(): SpinnerConfigObject {
            required("id", id)
            required("title", title)
            required("items", items)

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

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

    inner class CustomConfigBuilder: ConfigBuilder(){
        var onInflate: ((view: View) -> Unit)? = null

        override fun build(): CustomConfigObject {
            required("id", id)
            required("onInflate", onInflate)

            return CustomConfigObject(
                id = id!!,
                onInflate = onInflate!!
            )
        }
    }
}