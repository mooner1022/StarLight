package dev.mooner.configdsl

import android.graphics.Color
import android.util.Log
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
        private var _id: String? = null
        var id: String
            get() = _id ?: error("Config category '${title ?: "(untitled)"}' is missing an id")
            set(value) {
                _id = value
            }
        var title: String? = null
        @ColorInt
        var textColor: Int? = null
        var flags: Int = CategoryConfigOption.FLAG_NONE

        var items: List<ConfigOption<*, *>> = arrayListOf()

        fun items(block: ConfigItemBuilder.() -> Unit) {
            val builder = ConfigItemBuilder().apply(block)
            items = builder.build()
        }

        fun build(): CategoryConfigOption {
            require(items.isNotEmpty()) { "Category '$id' must have at least one item" }
            validateItems(id, items)

            return CategoryConfigOption(
                id = id,
                title = title ?: "",
                flags = flags,
                textColor = textColor,
                childOptions = items
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
        objects += builder.build().also(builder::applyExtras)
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

private fun validateItems(categoryId: String, items: List<ConfigOption<*, *>>) {
    val ids: MutableSet<String> = hashSetOf()
    for (item in items) {
        require(':' !in item.id) { "Config option id '${item.id}' in category '$categoryId' must not contain ':'" }
        if (!ids.add(item.id))
            Log.w(LOG_TAG, "Duplicated config option id '${item.id}' in category '$categoryId'")
    }
    for (item in items) {
        val dependency = item.dependency?.removePrefix("!") ?: continue
        if (dependency !in ids)
            Log.w(LOG_TAG, "Config option '${item.id}' in category '$categoryId' depends on unknown option '$dependency'")
    }
}

private const val LOG_TAG = "ConfigDSL"
