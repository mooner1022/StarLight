package com.mooner.starlight.plugincore.config

import android.text.InputType
import android.view.View
import androidx.annotation.ColorInt
import com.mooner.starlight.plugincore.utils.Icon

fun config(block: ConfigBuilder.() -> Unit): List<ConfigObject> {
    val builder = ConfigBuilder().apply(block)
    return builder.build(flush = true)
}

class ConfigBuilder {

    private val objects: MutableList<ConfigObject> = arrayListOf()

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

    fun title(block: TitleConfigBuilder.() -> Unit) {
        val title = TitleConfigBuilder().apply(block)
        objects.add(title.build())
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
        var icon: Icon = Icon.ARROW_RIGHT
        @ColorInt
        var iconTintColor: Int? = null
        @ColorInt
        var backgroundColor: Int? = null
        var dependency: String? = null

        fun build(): ButtonConfigObject {
            required("id", id)
            required("name", name)
            required("onClickListener", onClickListener)

            return ButtonConfigObject(
                id = id!!,
                name = name!!,
                onClickListener = onClickListener!!,
                buttonType = type,
                icon = icon,
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
        var dependency: String? = null

        fun build(): ToggleConfigObject {
            required("id", id)
            required("name", name)

            return ToggleConfigObject(
                id = id!!,
                name = name!!,
                defaultValue = defaultValue,
                dependency = dependency
            )
        }
    }

    inner class SliderConfigBuilder {
        var id: String? = null
        var name: String? = null
        var max: Int? = null
        var defaultValue: Int = 0
        var dependency: String? = null

        fun build(): SliderConfigObject {
            required("id", id)
            required("name", name)
            required("max", max)

            return SliderConfigObject(
                id = id!!,
                name = name!!,
                max = max!!,
                defaultValue = defaultValue,
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
        var dependency: String? = null

        fun build(): StringConfigObject{
            required("id", id)
            required("name", name)

            return StringConfigObject(
                id = id!!,
                name = name!!,
                hint = hint ?: "",
                inputType = inputType,
                require = require,
                dependency = dependency
            )
        }
    }

    inner class SpinnerConfigBuilder {
        var id: String? = null
        var name: String? = null
        var items: List<String>? = null
        var defaultIndex: Int = 0
        var dependency: String? = null

        fun build(): SpinnerConfigObject {
            required("id", id)
            required("name", name)
            required("items", items)

            return SpinnerConfigObject(
                id = id!!,
                name = name!!,
                items = items!!,
                defaultIndex = defaultIndex,
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

    inner class TitleConfigBuilder {
        var title: String? = null
        @ColorInt
        var textColor: Int = 0x000000

        fun build(): TitleConfigObject {
            required("title", title)

            return TitleConfigObject(
                title = title!!,
                textColor = textColor
            )
        }
    }
}