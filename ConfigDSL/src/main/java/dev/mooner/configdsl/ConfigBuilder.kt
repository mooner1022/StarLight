package dev.mooner.configdsl

import android.graphics.Color
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import dev.mooner.configdsl.options.CategoryConfigOption
import java.io.File
import kotlin.properties.Delegates.notNull

typealias LazyMessage = () -> String

typealias SpinnerItemSelectedListener = (view: View, index: Int) -> Unit
typealias ButtonOnClickListener       = (view: View) -> Unit
typealias ToggleValueChangedListener  = (view: View, toggle: Boolean) -> Unit
typealias ColorSelectedListener       = (view: View, color: Int) -> Unit

@DslMarker
annotation class ConfigBuilderDsl

@DslMarker
annotation class ConfigOptionBuilderDsl

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

    private val objects: MutableList<RootConfigOption<*, *>> = arrayListOf()

    fun color(color: () -> String): Int {
        val hex = color()
        return color(hex)
    }

    fun color(color: String): Int = colorCache[color] ?: let {
        val colorInt = Color.parseColor(color)
        colorCache[color] = colorInt
        colorInt
    }

    fun add(option: RootConfigOption<*, *>) {
        objects += option
    }

    @ConfigBuilderDsl
    fun category(block: CategoryConfigBuilder.() -> Unit) {
        val category = CategoryConfigBuilder().apply(block)
        objects += category.build()
    }

    fun combine(
        struct: ConfigStructure,
        filter: ((category: RootConfigOption<*, *>) -> Boolean)? = null
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
        var flags: Int = CategoryConfigOption.FLAG_NONE

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

        var items: List<ConfigOption<*, *>> = arrayListOf()

        @ConfigBuilderDsl
        fun items(block: ConfigItemBuilder.() -> Unit) {
            val builder = ConfigItemBuilder().apply(block)
            items = builder.build()
        }

        fun build(): CategoryConfigOption {
            require(items.isNotEmpty()) { "Field 'items' must not be empty" }

            return CategoryConfigOption(
                id = id,
                title = title ?: "",
                icon = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
                flags = flags,
                textColor = textColor,
                items = items
            )
        }
    }
}

@Suppress("unused")
class ConfigItemBuilder {

    private val objects: MutableList<ConfigOption<*, *>> = arrayListOf()

    operator fun plusAssign(option: ConfigOption<*, *>) {
        objects += option
    }

    fun add(builder: ConfigBuilderBase<*, *>) {
        objects += builder.build()
    }

    fun build(): List<ConfigOption<*, *>> {
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

        abstract fun build(): ConfigOption<*, *>
    }
}