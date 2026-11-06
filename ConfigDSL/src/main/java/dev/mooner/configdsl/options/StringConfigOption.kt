/*
 * StringConfigOption.kt created by Minki Moon(mooner1022) on 2/17/24, 2:29 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.options

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import dev.mooner.configdsl.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

data class StringConfigOption(
    override val id           : String,
    override val title        : String,
    override val description  : String? = null,
             val hint         : String,
             val inputType    : Int = InputType.TYPE_CLASS_TEXT,
             val require      : (String) -> String? = { null },
    override val default      : String = "",
    override val icon         : IconInfo,
    override val dependency   : String? = null,
): ConfigOption<StringConfigOption.StringViewHolder, String>() {

    override var hasError: Boolean = false
        private set

    override fun onCreateViewHolder(parent: ViewGroup): StringViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_string, parent, false)
        return StringViewHolder(view)
    }

    override fun dataToJson(value: String): JsonElement =
        JsonPrimitive(value)

    override fun dataFromJson(jsonElement: JsonElement): String =
        jsonElement.jsonPrimitive.content

    override fun onDraw(holder: StringViewHolder, data: String) {
        val editText = holder.editTextString
        holder.onTextChanged = null
        editText.hint = hint
        if (editText.inputType != inputType)
            editText.inputType = inputType
        if (editText.text.toString() != data)
            editText.setText(data)
        holder.onTextChanged = { text -> onTextUpdate(editText, text) }
    }

    private fun onTextUpdate(editText: EditText, value: String) {
        val require: String?
        if (require(value).also { require = it } == null) {
            if (hasError)
                hasError = false
            notifyUpdated(value)
        } else {
            if (!hasError)
                hasError = true
            editText.error = require!!
        }
    }

    class StringViewHolder(itemView: View): DefaultViewHolder(itemView) {

        val editTextString: EditText = itemView.findViewById(R.id.editText)

        internal var onTextChanged: ((String) -> Unit)? = null

        init {
            editTextString.doAfterTextChanged { text -> onTextChanged?.invoke(text.toString()) }
        }

        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            editTextString.isEnabled = enabled
        }
    }
}

class StringConfigBuilder: ConfigBuilderBase<StringConfigOption.StringViewHolder, String>() {
    var hint: String? = null
    var defaultValue: String? = null
    var inputType: Int = InputType.TYPE_CLASS_TEXT
    var require: (String) -> String? = { null }

    override fun build(): StringConfigOption {
        requiredField("title", title)

        return StringConfigOption(
            id = id,
            title = title!!,
            description = description,
            hint = hint ?: "",
            default = defaultValue ?: "",
            inputType = inputType,
            require = require,
            icon = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            dependency = dependency,
        )
    }
}

fun ConfigItemBuilder.string(block: StringConfigBuilder.() -> Unit) {
    add(StringConfigBuilder().apply(block))
}