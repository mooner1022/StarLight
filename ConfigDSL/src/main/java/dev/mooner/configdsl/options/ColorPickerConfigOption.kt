/*
 * ColorPickerConfigOption.kt created by Minki Moon(mooner1022) on 2/17/24, 2:36 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.options

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.color.colorChooser
import dev.mooner.configdsl.*
import dev.mooner.configdsl.utils.hasFlag
import dev.mooner.configdsl.utils.setCommonAttrs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

data class ColorPickerConfigOption(
    override val id           : String,
    override val title        : String,
    override val description  : String? = null,
    val onColorSelectedListener: ColorSelectedListener? = null,
             val flags        : Int = FLAG_NONE,
             val colors       : Map<Int, Array<Int>>,
    override val default      : Int = 0x0,
    override val icon         : IconInfo,
    override val dependency   : String? = null,
): ConfigOption<ButtonConfigOption.ButtonViewHolder, Int>() {

    override fun onCreateViewHolder(parent: ViewGroup): ButtonConfigOption.ButtonViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_button, parent, false)
        return ButtonConfigOption.ButtonViewHolder(view)
    }

    override fun dataToJson(value: Int): JsonElement =
        JsonPrimitive(value)

    override fun dataFromJson(jsonElement: JsonElement): Int =
        jsonElement.jsonPrimitive.int

    override fun onDraw(holder: ButtonConfigOption.ButtonViewHolder, data: Int) {
        val mainColors = colors.keys.toIntArray()
        val subColors  = colors.map { it.value.toIntArray() }.toTypedArray()

        val allowCustomArgb   = flags hasFlag FLAG_CUSTOM_ARGB
        val showAlphaSelector = allowCustomArgb && flags hasFlag FLAG_ALPHA_SELECTOR

        val initialSelection = data.let { def -> if (def == 0x0) null else def }
        var selected: Int? = null

        holder.icon.imageTintList = initialSelection?.let(ColorStateList::valueOf)
        holder.layout.setOnClickListener { view ->
            MaterialDialog(view.context, BottomSheet(LayoutMode.WRAP_CONTENT))
                .colorChooser(
                    colors = mainColors,
                    subColors = subColors,
                    allowCustomArgb = allowCustomArgb,
                    showAlphaSelector = showAlphaSelector,
                    initialSelection = initialSelection
                ) { _, color ->
                    selected = color
                }.show {
                    setCommonAttrs()
                    title(text = title)

                    positiveButton(R.string.ok) {
                        if (selected == null) {
                            Toast.makeText(context, "선택된 값이 없어 저장되지 않았어요.", Toast.LENGTH_SHORT).show()
                        } else {
                            holder.icon.imageTintList = selected?.let(ColorStateList::valueOf)
                            onColorSelectedListener?.invoke(holder.layout, selected!!)
                            notifyUpdated(selected!!)
                        }
                    }
                    negativeButton(R.string.cancel)
                }
        }
    }

    companion object {

        const val FLAG_NONE = 0x0

        const val FLAG_CUSTOM_ARGB = 0x1

        const val FLAG_ALPHA_SELECTOR = 0x2
    }
}

class ColorPickerConfigBuilder: ConfigBuilderBase<ButtonConfigOption.ButtonViewHolder, Int>() {

    private var colors: Map<Int, Array<Int>> = mapOf()

    private var onColorSelectedListener: ColorSelectedListener? = null

    var flags: Int = ColorPickerConfigOption.FLAG_NONE

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

    override fun build(): ColorPickerConfigOption =
        ColorPickerConfigOption(
            id = id,
            title = title!!,
            description = description,
            icon = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            default = defaultSelection,
            colors = colors,
            flags = flags,
            onColorSelectedListener = onColorSelectedListener,
            dependency = dependency
        )

    inner class ColorBuilder {

        private val colors: MutableMap<Int, Array<Int>> = mutableMapOf()

        fun color(color: Int, vararg subColors: Int) {
            colors[color] = subColors.toTypedArray()
        }

        internal fun build() = colors
    }
}

@ConfigOptionBuilderDsl
fun ConfigItemBuilder.colorPicker(block: ColorPickerConfigBuilder.() -> Unit) {
    add(ColorPickerConfigBuilder().apply(block))
}