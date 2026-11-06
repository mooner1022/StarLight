/*
 * PasswordConfigOption.kt created by Minki Moon(mooner1022) on 2/17/24, 2:30 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.options

import android.view.LayoutInflater
import android.view.ViewGroup
import dev.mooner.configdsl.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.properties.Delegates

data class PasswordConfigOption(
    override val id           : String,
    override val title        : String,
    override val description  : String? = null,
    val require      : (value: String) -> String? = { null },
    val hashCode     : (value: String) -> String,
    override val icon         : IconInfo,
    override val dependency   : String? = null,
): ConfigOption<ButtonConfigOption.ButtonViewHolder, String>() {

    override val default: String = ""
    override var hasError: Boolean = false
        private set

    override fun onCreateViewHolder(parent: ViewGroup): ButtonConfigOption.ButtonViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_button, parent, false)
        return ButtonConfigOption.ButtonViewHolder(view)
    }

    override fun dataToJson(value: String): JsonElement =
        JsonPrimitive(value)

    override fun dataFromJson(jsonElement: JsonElement): String =
        jsonElement.jsonPrimitive.content

    override fun onDraw(holder: ButtonConfigOption.ButtonViewHolder, data: String) {
        holder.layout.setOnClickListener {
            if (!holder.layout.isEnabled)
                return@setOnClickListener
            ConfigDSL.dialogProvider.password(
                context = it.context,
                title = displayTitle,
                validate = { text ->
                    require(text).also { error -> hasError = error != null }
                },
                onConfirm = { password -> notifyUpdated(hashCode(password)) },
            )
        }
    }
}

class PasswordConfigBuilder: ConfigBuilderBase<ButtonConfigOption.ButtonViewHolder, String>() {
    var require: (value: String) -> String? = { null }
    var hashCode: ((value: String) -> String) by Delegates.notNull()

    override fun build(): PasswordConfigOption {
        requiredField("title", title)

        return PasswordConfigOption(
            id = id,
            title = title!!,
            description = description,
            require = require,
            hashCode = hashCode,
            icon = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            dependency = dependency,
        )
    }
}

fun ConfigItemBuilder.password(block: PasswordConfigBuilder.() -> Unit) {
    add(PasswordConfigBuilder().apply(block))
}