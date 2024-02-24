/*
 * ToggleConfigOption.kt created by Minki Moon(mooner1022) on 2/17/24, 2:26 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.options

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import dev.mooner.configdsl.*
import dev.mooner.configdsl.utils.showConfirmDialog
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

data class ToggleConfigOption(
    override val id             : String,
    override val title          : String,
    override val description    : String?,
    override val default        : Boolean,
    val onValueChangedListener  : ToggleValueChangedListener?,
    val enableWarnMsg           : LazyMessage?,
    val disableWarnMag          : LazyMessage?,
    override val icon           : IconInfo,
    override val dependency     : String? = null,
): ConfigOption<ToggleConfigOption.ToggleViewHolder, Boolean>() {

    override fun onCreateViewHolder(parent: ViewGroup): ToggleViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_toggle, parent, false)
        return ToggleViewHolder(view)
    }

    override fun dataToJson(value: Boolean): JsonElement =
        JsonPrimitive(value)

    override fun dataFromJson(jsonElement: JsonElement): Boolean =
        jsonElement.jsonPrimitive.boolean

    override fun onDraw(holder: ToggleViewHolder, data: Boolean) {
        holder.toggle.isChecked = data
        holder.toggle.setOnCheckedChangeListener { _, isChecked ->
            if (enableWarnMsg != null || disableWarnMag != null) {
                (if (isChecked) enableWarnMsg else disableWarnMag)?.let { lazyMessage ->
                    showConfirmDialog(holder.toggle.context, title = "경고", message = lazyMessage()) { isConfirmed ->
                        if (isConfirmed) {
                            onChecked(holder.toggle, isChecked)
                        } else {
                            holder.toggle.isChecked = !holder.toggle.isChecked
                        }
                    }
                } ?: onChecked(holder.toggle, isChecked)
            } else {
                onChecked(holder.toggle, isChecked)
            }
        }
    }

    private fun onChecked(toggle: SwitchMaterial, isChecked: Boolean) {
        onValueChangedListener?.invoke(toggle, isChecked)
        println(notifyUpdated(isChecked))
        notifyDependencyUpdated(isChecked)
    }

    class ToggleViewHolder(itemView: View): DefaultViewHolder(itemView) {

        val toggle: SwitchMaterial = itemView.findViewById(R.id.toggle)

        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            toggle.isEnabled = enabled
        }
    }
}

class ToggleConfigBuilder: ConfigBuilderBase<ToggleConfigOption.ToggleViewHolder, Boolean>() {
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

    override fun build(): ToggleConfigOption {
        requiredField("title", title)

        return ToggleConfigOption(
            id = id,
            title = title!!,
            description = description,
            default = defaultValue,
            onValueChangedListener = toggleValueChangedListener,
            enableWarnMsg = enableWarnMsg,
            disableWarnMag = disableWarnMsg,
            icon = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            dependency = dependency,
        )
    }
}

@ConfigBuilderDsl
fun ConfigItemBuilder.toggle(block: ToggleConfigBuilder.() -> Unit) {
    add(ToggleConfigBuilder().apply(block))
}