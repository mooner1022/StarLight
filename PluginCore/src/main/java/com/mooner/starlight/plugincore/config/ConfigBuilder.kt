package com.mooner.starlight.plugincore.config

import android.graphics.Color
import android.text.InputType
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.mooner.starlight.plugincore.utils.Icon
import com.mooner.starlight.plugincore.utils.requiredField
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

    inner class CategoryConfigBuilder {
        var id: String? = null
        var title: String? = null
        @ColorInt
        var textColor: Int? = null
        var flags: Int = CategoryConfigObject.FLAG_NONE

        var icon: Icon? = null
        var iconFile: File? = null
        @DrawableRes
        var iconResId: Int? = null

        @ColorInt
        var iconTintColor: Int? = Color.parseColor("#000000")

        fun setIcon(icon: Icon? = null, iconFile: File? = null, @DrawableRes iconResId: Int? = null) {
            when {
                icon != null -> this.icon = icon
                iconFile != null -> this.iconFile = iconFile
                iconResId != null -> this.iconResId = iconResId
            }
        }

        var items: List<ConfigObject> = arrayListOf()

        fun build(): CategoryConfigObject {
            requiredField("id", id)
            //required("title", title)
            require(items.isNotEmpty()) { "Field 'items' must not be empty" }

            return CategoryConfigObject(
                id = id!!,
                title = title?: "",
                icon = icon,
                iconFile = iconFile,
                iconResId = iconResId,
                iconTintColor = iconTintColor,
                flags = flags,
                textColor = textColor,
                items = items
            )
        }
    }
}

class ConfigItemBuilder {

    companion object {

        @JvmStatic
        private val colorCache: MutableMap<String, Int> = hashMapOf()
    }

    private val objects: MutableList<ConfigObject> = arrayListOf()

    fun color(color: () -> String): Int {
        val hex = color()
        return color(hex)
    }

    fun color(color: String): Int {
        if (color !in colorCache) {
            colorCache[color] = Color.parseColor(color)
        }
        return colorCache[color]!!
    }

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

    fun seekbar(block: SeekbarConfigBuilder.() -> Unit) {
        val seekbar = SeekbarConfigBuilder().apply(block)
        add(seekbar)
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
            requiredField("id", id)
            requiredField("title", title)
            requiredField("onClickListener", onClickListener)

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
            requiredField("id", id)
            requiredField("title", title)

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

    inner class SeekbarConfigBuilder: ConfigBuilder() {
        var max: Int? = null
        var min: Int = 0
        var defaultValue: Int = 0

        override fun build(): SeekbarConfigObject {
            requiredField("id", id)
            requiredField("title", title)
            requiredField("max", max)

            if (max!! <= 0) {
                throw IllegalArgumentException("Value of parameter 'max' should be positive.")
            }
            if (min > max!!) {
                throw IllegalArgumentException("Value of parameter 'min' should be smaller than 'max'.")
            }
            if (defaultValue !in min..max!!) {
                throw IllegalArgumentException("Value of parameter 'defaultValue' should be in range $min~$max.")
            }

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

            return SeekbarConfigObject(
                id = id!!,
                title = title!!,
                description = description,
                max = max!!,
                min = min,
                default = defaultValue,
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
            requiredField("id", id)
            requiredField("title", title)

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

        override fun build(): PasswordConfigObject {
            requiredField("id", id)
            requiredField("title", title)
            requiredField("hashCode", hashCode)

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
            requiredField("id", id)
            requiredField("title", title)
            requiredField("items", items)

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
            requiredField("id", id)
            requiredField("onInflate", onInflate)

            return CustomConfigObject(
                id = id!!,
                onInflate = onInflate!!
            )
        }
    }
}