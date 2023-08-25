package dev.mooner.starlight.plugincore.config

import android.graphics.Color
import android.text.InputType
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import dev.mooner.starlight.plugincore.config.data.PrimitiveTypedString
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.plugincore.utils.requiredField
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.properties.Delegates.notNull

typealias LazyMessage = () -> String

typealias SpinnerItemSelectedListener = (view: View, index: Int) -> Unit
typealias ButtonOnClickListener       = (view: View) -> Unit
typealias ToggleValueChangedListener  = (view: View, toggle: Boolean) -> Unit
typealias ColorSelectedListener       = (view: View, color: Int) -> Unit

@DslMarker
annotation class ConfigBuilderDsl

@ConfigBuilderDsl
fun config(block: ConfigBuilder.() -> Unit): ConfigStructure {
    val builder = ConfigBuilder().apply(block)
    return builder.build(flush = true)
}

@Suppress("unused")
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
        objects += category.build()
    }

    fun combine(
        struct: ConfigStructure,
        filter: ((category: CategoryConfigObject) -> Boolean)? = null
    ) {
        objects += if (filter != null)
            struct.filter(filter)
        else
            struct
    }

    fun build(flush: Boolean = true): ConfigStructure {
        val list = objects.toList()
        if (flush) {
            objects.clear()
        }
        return list
    }

    class CategoryConfigBuilder {
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
            items = builder.build()
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

@Suppress("unused")
class ConfigItemBuilder {

    private val objects: MutableList<ConfigObject> = arrayListOf()

    private fun add(builder: ConfigBuilder) {
        objects += builder.build()
    }

    @ConfigBuilderDsl
    fun button(block: ButtonConfigBuilder.() -> Unit) =
        add(ButtonConfigBuilder().apply(block))

    @ConfigBuilderDsl
    fun toggle(block: ToggleConfigBuilder.() -> Unit) =
        add(ToggleConfigBuilder().apply(block))

    @ConfigBuilderDsl
    fun seekbar(block: SeekbarConfigBuilder.() -> Unit) =
        add(SeekbarConfigBuilder().apply(block))

    @ConfigBuilderDsl
    fun string(block: StringConfigBuilder.() -> Unit) =
        add(StringConfigBuilder().apply(block))

    @ConfigBuilderDsl
    fun password(block: PasswordConfigBuilder.() -> Unit) =
        add(PasswordConfigBuilder().apply(block))

    @ConfigBuilderDsl
    fun spinner(block: SpinnerConfigBuilder.() -> Unit) =
        add(SpinnerConfigBuilder().apply(block))

    @ConfigBuilderDsl
    fun colorPicker(block: ColorPickerConfigBuilder.() -> Unit) =
        add(ColorPickerConfigBuilder().apply(block))

    @ConfigBuilderDsl
    fun list(block: ListConfigBuilder.() -> Unit) =
        add(ListConfigBuilder().apply(block))

    @ConfigBuilderDsl
    fun custom(block: CustomConfigBuilder.() -> Unit) =
        add(CustomConfigBuilder().apply(block))

    internal fun build(): List<ConfigObject> {
        val cp = objects.toList()
        objects.clear()
        return cp
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
        private var mOnClickListener: ButtonOnClickListener? = null
        var type: ButtonConfigObject.Type = ButtonConfigObject.Type.FLAT
        @ColorInt
        var backgroundColor: Int? = null

        fun setOnClickListener(callback: ButtonOnClickListener) {
            mOnClickListener = callback
        }

        fun setOnClickListener(callback: () -> Unit) {
            mOnClickListener = {callback()}
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

        private var toggleValueChangedListener: ToggleValueChangedListener? = null
        private var enableWarnMsg: LazyMessage? = null
        private var disableWarnMsg: LazyMessage? = null

        fun setOnValueChangedListener(callback: ToggleValueChangedListener) {
            toggleValueChangedListener = callback
        }

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
                onValueChangedListener = toggleValueChangedListener,
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
        var defaultValue: Int? = null

        override fun build(): SeekbarConfigObject {
            requiredField("title", title)

            require(max > 0) { "Value of parameter 'max' should be positive." }
            require(min < max) { "Value of parameter 'min' should be smaller than 'max'." }
            require(defaultValue in min..max) { "Value of parameter 'defaultValue' should be in range $min~$max." }

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

            return SeekbarConfigObject(
                id = id,
                title = title!!,
                description = description,
                max = max,
                min = min,
                default = defaultValue ?: min,
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

        private var onItemSelectedListener: SpinnerItemSelectedListener? = null

        fun setOnItemSelectedListener(callback: SpinnerItemSelectedListener) {
            onItemSelectedListener = callback
        }

        override fun build(): SpinnerConfigObject {
            requiredField("title", title)
            require(items.isNotEmpty()) { "Field 'items' must not be empty" }

            if (icon == null && iconFile == null && iconResId == null) icon = Icon.NONE

            return SpinnerConfigObject(
                id = id,
                title = title!!,
                description = description,
                onItemSelectedListener = onItemSelectedListener,
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

    inner class ColorPickerConfigBuilder: ConfigBuilder() {

        private var colors: Map<Int, Array<Int>> = mapOf()

        private var onColorSelectedListener: ColorSelectedListener? = null

        var flags: Int = ColorPickerConfigObject.FLAG_NONE

        var defaultSelection: Int = 0x0

        fun colors(colors: Array<Int>) {
            this.colors = colors.associateWith { arrayOf() }
        }

        fun colors(colors: Map<Int, Array<Int>>) {
            this.colors = colors
        }

        fun setOnColorSelectedListener(callback: ColorSelectedListener) {
            onColorSelectedListener = callback
        }

        @OptIn(ExperimentalContracts::class)
        fun colors(builder: ColorBuilder.() -> Unit) {
            contract {
                callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
            }
            this.colors = ColorBuilder().apply(builder).build()
        }

        override fun build(): ConfigObject =
            ColorPickerConfigObject(
                id = id,
                title = title!!,
                description = description,
                icon = icon,
                iconFile = iconFile,
                iconResId = iconResId,
                dependency = dependency,
                default = defaultSelection,
                colors = colors,
                flags = flags,
                onColorSelectedListener = onColorSelectedListener
            )

        inner class ColorBuilder {

            private val colors: MutableMap<Int, Array<Int>> = mutableMapOf()

            fun color(color: Int, vararg subColors: Int) {
                colors[color] = subColors.toTypedArray()
            }

            internal fun build() = colors
        }
    }

    inner class ListConfigBuilder: ConfigBuilder() {

        private var onDrawBlock: OnDrawBlock by notNull()
        private var onInflateBlock: OnInflateBlock by notNull()
        private var configStructure: List<ConfigObject> by notNull()

        private var default: String = "[]"
        fun default(vararg entries: Map<String, PrimitiveTypedString>) {
            val objects = entries.asList()
            default = Json.encodeToString(objects)
        }

        fun onInflate(block: OnInflateBlock) {
            onInflateBlock = block
        }

        fun onDraw(block: OnDrawBlock) {
            onDrawBlock = block
        }

        fun structure(block: ConfigItemBuilder.() -> Unit) {
            configStructure = ConfigItemBuilder().apply(block).build()
        }

        override fun build(): ConfigObject =
            ListConfigObject(
                id = id,
                title = title!!,
                description = description,
                icon = icon,
                iconFile = iconFile,
                iconResId = iconResId,
                iconTintColor = iconTintColor,
                dependency = dependency,
                onInflate = onInflateBlock,
                onDraw = onDrawBlock,
                structure = configStructure,
                default = default
            )
    }

    inner class CustomConfigBuilder: ConfigBuilder() {
        var onInflate: ((view: View) -> Unit) by notNull()

        override fun build() = CustomConfigObject(
            id = id,
            onInflate = onInflate
        )
    }
}