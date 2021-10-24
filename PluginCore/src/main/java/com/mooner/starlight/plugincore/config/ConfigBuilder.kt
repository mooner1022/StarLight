package com.mooner.starlight.plugincore.config

import android.graphics.Color
import android.text.InputType
import android.view.View
import androidx.annotation.ColorInt
import com.mooner.starlight.plugincore.utils.Icon

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
            required("title", title)
            require(items.isNotEmpty()) { "Field [items] must not be empty" }

            return CategoryConfigObject(
                id = id!!,
                title = title!!,
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

    inner class ButtonConfigBuilder {
        var id: String? = null
        var name: String? = null
        var onClickListener: ((view: View) -> Unit)? = null
        var type: ButtonConfigObject.Type = ButtonConfigObject.Type.FLAT
        var icon: Icon? = null
        @ColorInt
        var iconTintColor: Int = Color.parseColor("#000000")
        @ColorInt
        var backgroundColor: Int? = null
        var dependency: String? = null

        fun build(): ButtonConfigObject {
            required("id", id)
            required("name", name)
            required("onClickListener", onClickListener)
            required("icon", icon)

            return ButtonConfigObject(
                id = id!!,
                name = name!!,
                onClickListener = onClickListener!!,
                buttonType = type,
                icon = icon!!,
                iconTintColor = iconTintColor,
                backgroundColor = backgroundColor,
                dependency = dependency
            )
        }
    }

    inner class ToggleConfigBuilder {
        var id: String? = null
        var name: String? = null
        var defaultValue: Boolean = false
        var icon: Icon? = null
        @ColorInt
        var iconTintColor: Int = Color.parseColor("#000000")
        var dependency: String? = null

        fun build(): ToggleConfigObject {
            required("id", id)
            required("name", name)
            required("icon", icon)

            return ToggleConfigObject(
                id = id!!,
                name = name!!,
                defaultValue = defaultValue,
                icon = icon!!,
                iconTintColor = iconTintColor,
                dependency = dependency
            )
        }
    }

    inner class SliderConfigBuilder {
        var id: String? = null
        var name: String? = null
        var max: Int? = null
        var defaultValue: Int = 0
        var icon: Icon? = null
        @ColorInt
        var iconTintColor: Int = Color.parseColor("#000000")
        var dependency: String? = null

        fun build(): SliderConfigObject {
            required("id", id)
            required("name", name)
            required("max", max)
            required("icon", icon)

            return SliderConfigObject(
                id = id!!,
                name = name!!,
                max = max!!,
                defaultValue = defaultValue,
                icon = icon!!,
                iconTintColor = iconTintColor,
                dependency = dependency
            )
        }
    }

    inner class StringConfigBuilder {
        var id: String? = null
        var name: String? = null
        var hint: String? = null
        var inputType: Int = InputType.TYPE_CLASS_TEXT
        var require: (String) -> String? = { null }
        var icon: Icon? = null
        @ColorInt
        var iconTintColor: Int = Color.parseColor("#000000")
        var dependency: String? = null

        fun build(): StringConfigObject{
            required("id", id)
            required("name", name)
            required("icon", icon)

            return StringConfigObject(
                id = id!!,
                name = name!!,
                hint = hint ?: "",
                inputType = inputType,
                require = require,
                icon = icon!!,
                iconTintColor = iconTintColor,
                dependency = dependency
            )
        }
    }

    inner class SpinnerConfigBuilder {
        var id: String? = null
        var name: String? = null
        var items: List<String>? = null
        var defaultIndex: Int = 0
        var icon: Icon? = null
        @ColorInt
        var iconTintColor: Int = Color.parseColor("#000000")
        var dependency: String? = null

        fun build(): SpinnerConfigObject {
            required("id", id)
            required("name", name)
            required("items", items)
            required("icon", icon)

            return SpinnerConfigObject(
                id = id!!,
                name = name!!,
                items = items!!,
                defaultIndex = defaultIndex,
                icon = icon!!,
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