/*
 * SpinnerConfigOption.kt created by Minki Moon(mooner1022) on 2/17/24, 2:33 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.options

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import dev.mooner.configdsl.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min
import kotlin.properties.Delegates

data class SpinnerConfigOption(
    override val id           : String,
    override val title        : String,
    override val description  : String? = null,
    val onItemSelectedListener: SpinnerItemSelectedListener?,
             val items        : List<String>,
    private  val defaultIndex : Int = 0,
    override val icon         : IconInfo,
    override val dependency   : String? = null,
): ConfigOption<SpinnerConfigOption.SpinnerViewHolder, Int>() {

    override val default: Int
        get() = defaultIndex

    override fun onCreateViewHolder(parent: ViewGroup): SpinnerViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_spinner, parent, false)
        return SpinnerViewHolder(view)
    }

    override fun dataToJson(value: Int): JsonElement =
        JsonPrimitive(value)

    override fun dataFromJson(jsonElement: JsonElement): Int =
        jsonElement.jsonPrimitive.int

    override fun onDraw(holder: SpinnerViewHolder, data: Int) {
        holder.spinner.apply {
            val items = ArrayAdapter(context, android.R.layout.simple_spinner_item, items)
            val selectionIdx = min(data, items.count - 1)
            if (selectionIdx != data)
                Log.d("SpinnerConfigOption", "Spinner idx out of bound, fixed to item size...")

            adapter = items
            setBackgroundColor(context.getColor(R.color.transparent))
            setSelection(selectionIdx, true)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                    this@SpinnerConfigOption.onItemSelectedListener?.invoke(this@apply, position)
                    notifyUpdated(position)
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }
    }

    class SpinnerViewHolder(itemView: View): DefaultViewHolder(itemView) {

        val spinner: Spinner = itemView.findViewById(R.id.spinner)

        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            spinner.isEnabled = false
        }
    }
}

class SpinnerConfigBuilder: ConfigBuilderBase<SpinnerConfigOption.SpinnerViewHolder, Int>() {
    var items: List<String> by Delegates.notNull()
    var defaultIndex: Int = 0

    private var onItemSelectedListener: SpinnerItemSelectedListener? = null

    fun setOnItemSelectedListener(callback: SpinnerItemSelectedListener) {
        onItemSelectedListener = callback
    }

    override fun build(): SpinnerConfigOption {
        requiredField("title", title)
        require(items.isNotEmpty()) { "Field 'items' must not be empty" }

        return SpinnerConfigOption(
            id = id,
            title = title!!,
            description = description,
            onItemSelectedListener = onItemSelectedListener,
            items = items,
            defaultIndex = defaultIndex,
            icon = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            dependency = dependency,
        )
    }
}

@ConfigBuilderDsl
fun ConfigItemBuilder.spinner(block: SpinnerConfigBuilder.() -> Unit) {
    add(SpinnerConfigBuilder().apply(block))
}