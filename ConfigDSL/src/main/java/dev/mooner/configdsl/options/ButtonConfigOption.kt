/*
 * ButtonConfigOption.kt created by Minki Moon(mooner1022) on 2/17/24, 2:35 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.options

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import dev.mooner.configdsl.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

data class ButtonConfigOption(
    override val id              : String,
    override val title           : String,
    override val description     : String? = null,
             val onClickListener : ButtonOnClickListener?,
             @ColorInt
             val backgroundColor : Int? = null,
    override val icon            : IconInfo,
    override val dependency      : String? = null,
): ConfigOption<ButtonConfigOption.ButtonViewHolder, Boolean>() {

    override val default: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup): ButtonViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_button, parent, false)
        return ButtonViewHolder(view)
    }

    override fun dataToJson(value: Boolean): JsonElement =
        JsonPrimitive(null)

    override fun dataFromJson(jsonElement: JsonElement): Boolean =
        false

    override fun onDraw(holder: ButtonViewHolder, data: Boolean) {
        holder.layout.setOnClickListener(onClickListener)
    }

    class ButtonViewHolder(itemView: View): DefaultViewHolder(itemView) {

        val layout: ConstraintLayout = itemView.findViewById(R.id.layout_configButton)
    }
}

class ButtonConfigBuilder: ConfigBuilderBase<ButtonConfigOption.ButtonViewHolder, Boolean>() {

    private var mOnClickListener: ButtonOnClickListener? = null
    @ColorInt
    var backgroundColor: Int? = null

    fun setOnClickListener(callback: ButtonOnClickListener) {
        mOnClickListener = callback
    }

    fun setOnClickListener(callback: () -> Unit) {
        mOnClickListener = { callback() }
    }

    override fun build(): ButtonConfigOption {
        requiredField("title", title)

        return ButtonConfigOption(
            id = id,
            title = title!!,
            description = description,
            onClickListener = mOnClickListener,
            icon = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            backgroundColor = backgroundColor,
            dependency = dependency
        )
    }
}

@ConfigBuilderDsl
fun ConfigItemBuilder.button(block: ButtonConfigBuilder.() -> Unit) {
    add(ButtonConfigBuilder().apply(block))
}