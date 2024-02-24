/*
 * SeekbarConfigOption.kt created by Minki Moon(mooner1022) on 2/17/24, 2:27 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.options

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import dev.mooner.configdsl.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.properties.Delegates

data class SeekbarConfigOption(
    override val id           : String,
    override val title        : String,
    override val description  : String? = null,
             val max          : Int,
             val min          : Int = 0,
    override val default      : Int,
    override val icon         : IconInfo,
    override val dependency   : String? = null,
): ConfigOption<SeekbarConfigOption.SeekbarViewHolder, Int>() {

    override fun onCreateViewHolder(parent: ViewGroup): SeekbarViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_slider, parent, false)
        return SeekbarViewHolder(view)
    }

    override fun dataToJson(value: Int): JsonElement =
        JsonPrimitive(value)

    override fun dataFromJson(jsonElement: JsonElement): Int =
        jsonElement.jsonPrimitive.int

    override fun onDraw(holder: SeekbarViewHolder, data: Int) {
        val offset = min
        holder.seekBar.progress  = data
        holder.seekBarIndex.text = (holder.seekBar.progress).toString()
        holder.seekBar.max       = max - offset

        holder.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                holder.seekBarIndex.text = (progress + offset).toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                notifyUpdated(seekBar.progress + offset)
            }
        })
    }

    class SeekbarViewHolder(itemView: View): DefaultViewHolder(itemView) {

        val seekBarIndex: TextView = itemView.findViewById(R.id.index)
        val seekBar: SeekBar = itemView.findViewById(R.id.slider)

        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            seekBar.isEnabled      = enabled
            seekBarIndex.isEnabled = enabled
        }
    }
}

class SeekbarConfigBuilder: ConfigBuilderBase<SeekbarConfigOption.SeekbarViewHolder, Int>() {
    var max: Int by Delegates.notNull()
    var min: Int = 0
    var defaultValue: Int? = null

    override fun build(): SeekbarConfigOption {
        requiredField("title", title)

        require(max > 0) { "Value of parameter 'max' should be positive." }
        require(min < max) { "Value of parameter 'min' should be smaller than 'max'." }
        require(defaultValue in min..max) { "Value of parameter 'defaultValue' should be in range $min~$max." }

        return SeekbarConfigOption(
            id = id,
            title = title!!,
            description = description,
            max = max,
            min = min,
            default = defaultValue ?: min,
            icon = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            dependency = dependency,
        )
    }
}

@ConfigBuilderDsl
fun ConfigItemBuilder.seekbar(block: SeekbarConfigBuilder.() -> Unit) {
    add(SeekbarConfigBuilder().apply(block))
}