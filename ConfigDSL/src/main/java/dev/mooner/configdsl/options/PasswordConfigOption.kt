/*
 * PasswordConfigOption.kt created by Minki Moon(mooner1022) on 2/17/24, 2:30 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.options

import android.view.LayoutInflater
import android.view.ViewGroup
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import dev.mooner.configdsl.*
import dev.mooner.configdsl.utils.setCommonAttrs
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
            MaterialDialog(it.context, BottomSheet(LayoutMode.WRAP_CONTENT))
                .input(waitForPositiveButton = false) { dialog, text ->
                    val require = require(text.toString())
                    if (require != null) {
                        dialog.getInputField().error = require
                        return@input
                    }
                }.show {
                    setCommonAttrs()
                    title(text = title)

                    positiveButton(text = "설정") { dialog ->
                        val password = dialog.getInputField().text.toString()
                        val encoded = hashCode(password)
                        notifyUpdated(encoded)
                    }
                    negativeButton(text = "취소", click = MaterialDialog::dismiss)
                }
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

@ConfigOptionBuilderDsl
fun ConfigItemBuilder.password(block: PasswordConfigBuilder.() -> Unit) {
    add(PasswordConfigBuilder().apply(block))
}