package dev.mooner.starlight.plugincore.config

import android.graphics.Color
import android.text.InputType
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.plugincore.utils.requiredField
import java.io.File
import kotlin.properties.Delegates.notNull

typealias LazyMessage = () -> String

@DslMarker
public annotation class ConfigBuilderDsl

@ConfigBuilderDsl
fun config(block: ConfigBuilder.() -> Unit): List<CategoryConfigObject> {
    val builder = ConfigBuilder().apply(block)
    return builder.build(flush = true)
}

class ConfigBuilder {

    companion object {

        @JvmStatic
        private val colorCache: MutableMap<String, Int> = hashMapOf()
    }

    private val objects: MutableList<CategoryConfigObject> = arrayListOf()

    fun color(color: () -> String): Int {
        val hex = color()
        return color(hex)
    }

    fun color(color: String): Int = colorCache[color] ?: let {
        val colorInt = Color.parseColor(color)
        colorCache[color] = colorInt
        colorInt
    }

    @ConfigBuilderDsl
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

    inner class CategoryConfigBuilder {
        var id: String by notNull()
        var title: String? = null
        @ColorInt
        var textColor: Int? = null
        var flags: Int = CategoryConfigObject.FLAG_NONE

        var icon: Icon? = null
        var iconFile: File? = null
        @DrawableRes
        var iconResId: Int? = null

        @ColorInt
        var iconTintColor: Int? = null

        fun setIcon(icon: Icon? = null, iconFile: File? = null, @DrawableRes iconResId: Int? = null) {
            when {
                icon != null -> this.icon = icon
                iconFile != null -> this.iconFile = iconFile
                iconResId != null -> this.iconResId = iconResId
            }
        }

        var items: List<ConfigObject> = arrayListOf()

        @ConfigBuilderDsl
        fun items(block: ConfigItemBuilder.() -> Unit) {
            val builder = ConfigItemBuilder().apply(block)
            items = builder.build(flush = true)
        }

        fun build(): CategoryConfigObject {
            require(items.isNotEmpty()) { "Field 'items' must not be empty" }

            return CategoryConfigObject(
                id = id,
                title = title ?: "",
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

    private val objects: MutableList<ConfigObject> = arrayListOf()

    private fun add(builder: ConfigBuilder) {
        objects += builder.build()
    }

    @ConfigBuilderDsl
    fun button(block: ButtonConfigBuilder.() -> Unit) {
        val button = ButtonConfigBuilder().apply(block)
        add(button)
    }

    @ConfigBuilderDsl
    fun toggle(block: ToggleConfigBuilder.() -> Unit) {
        val toggle = ToggleConfigBuilder().apply(block)
        add(toggle)
    }

    @ConfigBuilderDsl
    fun seekbar(block: SeekbarConfigBuilder.() -> Unit) {
        val seekbar = SeekbarConfigBuilder().apply(block)
        add(seekbar)
    }

    @ConfigBuilderDsl
    fun string(block: StringConfigBuilder.() -> Unit) {
        val string = StringConfigBuilder().apply(block)
        add(string)
    }

    @ConfigBuilderDsl
    fun password(block: PasswordConfigBuilder.() -> Unit) {
        val pw = PasswordConfigBuilder().apply(block)
        add(pw)
    }

    @ConfigBuilderDsl
    fun spinner(block: SpinnerConfigBuilder.() -> Unit) {
        val spinner = SpinnerConfigBuilder().apply(block)
        add(spinner)
    }

    @ConfigBuilderDsl
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
        var id: String by notNull()
        var title: String? = null
        var description: String? = null

        var icon: Icon? = null
        var iconFile: File? = null
        @DrawableRes
        var iconResId: Int? = null

        @ColorInt
        var iconTintColor: Int? = null

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
        private var mOnClickListener: ((view: View) -> Unit)? = null
        var type: ButtonConfigObject.Type = ButtonConfigObject.Type.FLAT
        @ColorInt
        var backgroundColor: Int? = null

        fun setOnClickListener(listener: (view: View) -> Unit) {
            mOnClickListener = listener
        }

        override fun build(): ButtonConfigObject {
            requiredField("title", title)

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

            return ButtonConfigObject(
                id = id,
                title = title!!,
                description = description,
                onClickListener = mOnClickListener,
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

        private var enableWarnMsg: LazyMessage? = null
        private var disableWarnMsg: LazyMessage? = null

        fun warnOnEnable(message: String) = warnOnEnable { message}

        fun warnOnEnable(lazyMessage: () -> String) {
            enableWarnMsg = lazyMessage
        }

        fun warnOnDisable(message: String) = warnOnDisable { message }

        fun warnOnDisable(lazyMessage: () -> String) {
            disableWarnMsg = lazyMessage
        }

        override fun build(): ToggleConfigObject {
            requiredField("title", title)

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

            return ToggleConfigObject(
                id = id,
                title = title!!,
                description = description,
                defaultValue = defaultValue,
                enableWarnMsg = enableWarnMsg,
                disableWarnMag = disableWarnMsg,
                icon = icon,
                iconFile = iconFile,
                iconResId = iconResId,
                iconTintColor = iconTintColor,
                dependency = dependency
            )
        }
    }

    inner class SeekbarConfigBuilder: ConfigBuilder() {
        var max: Int by notNull()
        var min: Int = 0
        var defaultValue: Int = 0

        override fun build(): SeekbarConfigObject {
            requiredField("title", title)

            if (max <= 0) {
                throw IllegalArgumentException("Value of parameter 'max' should be positive.")
            }
            if (min > max) {
                throw IllegalArgumentException("Value of parameter 'min' should be smaller than 'max'.")
            }
            if (defaultValue !in min..max) {
                throw IllegalArgumentException("Value of parameter 'defaultValue' should be in range $min~$max.")
            }

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

            return SeekbarConfigObject(
                id = id,
                title = title!!,
                description = description,
                max = max,
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
            requiredField("title", title)

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

            return StringConfigObject(
                id = id,
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
        var hashCode: ((value: String) -> String) by notNull()

        override fun build(): PasswordConfigObject {
            requiredField("title", title)

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

            return PasswordConfigObject(
                id = id,
                title = title!!,
                description = description,
                require = require,
                hashCode = hashCode,
                icon = icon,
                iconFile = iconFile,
                iconResId = iconResId,
                iconTintColor = iconTintColor,
                dependency = dependency
            )
        }
    }

    inner class SpinnerConfigBuilder: ConfigBuilder() {
        var items: List<String> by notNull()
        var defaultIndex: Int = 0

        override fun build(): SpinnerConfigObject {
            requiredField("title", title)
            require(items.isNotEmpty()) { "Field 'items' must not be empty" }

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

            return SpinnerConfigObject(
                id = id,
                title = title!!,
                description = description,
                items = items,
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
        var onInflate: ((view: View) -> Unit) by notNull()

        override fun build() = CustomConfigObject(
            id = id,
            onInflate = onInflate
        )
    }
}