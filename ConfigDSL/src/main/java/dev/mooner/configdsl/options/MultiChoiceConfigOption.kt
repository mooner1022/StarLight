package dev.mooner.configdsl.options

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.mooner.configdsl.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class MultiChoiceConfigOption(
    override val id          : String,
    override val title       : String,
    override val description : String?,
             val choices     : List<Choice>,
    override val default     : Set<String>,
    override val icon        : IconInfo,
    override val dependency  : String? = null,
): ConfigOption<MultiChoiceConfigOption.MultiChoiceViewHolder, Set<String>>() {

    override val hasError: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup): MultiChoiceViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_info, parent, false)
        return MultiChoiceViewHolder(view)
    }

    override fun dataToJson(value: Set<String>): JsonElement =
        JsonArray(value.map { JsonPrimitive(it) })

    override fun dataFromJson(jsonElement: JsonElement): Set<String> =
        jsonElement.jsonArray.mapTo(linkedSetOf()) { it.jsonPrimitive.content }

    override fun onDraw(holder: MultiChoiceViewHolder, data: Set<String>) {
        holder.selected = data
        holder.value.text = summaryOf(holder.context, data)
        holder.itemView.setOnClickListener {
            if (!holder.itemView.isEnabled)
                return@setOnClickListener
            val selected = choices.indices.filterTo(linkedSetOf()) { choices[it].key in holder.selected }
            ConfigDSL.dialogProvider.multiSelect(holder.context, displayTitle, choices.map(Choice::label), selected) { result ->
                result ?: return@multiSelect
                val keys = choices.filterIndexed { index, _ -> index in result }.mapTo(linkedSetOf(), Choice::key)
                holder.selected = keys
                holder.value.text = summaryOf(holder.context, keys)
                notifyUpdated(keys)
            }
        }
    }

    private fun summaryOf(context: Context, keys: Set<String>): String =
        choices
            .filter { it.key in keys }
            .joinToString { it.label }
            .ifEmpty { context.getString(R.string.configdsl_none_selected) }

    class MultiChoiceViewHolder(itemView: View): DefaultViewHolder(itemView) {

        val value: TextView = itemView.findViewById(R.id.value)

        internal var selected: Set<String> = emptySet()
    }
}

class MultiChoiceConfigBuilder: ConfigBuilderBase<MultiChoiceConfigOption.MultiChoiceViewHolder, Set<String>>() {

    var defaultKeys: Set<String> = emptySet()

    private val choices: MutableList<Choice> = arrayListOf()

    fun option(key: String, label: String) {
        choices += Choice(key, label)
    }

    override fun build(): MultiChoiceConfigOption {
        requiredField("title", title)
        require(choices.isNotEmpty()) { "Multi choice '$id' must have at least one option" }

        return MultiChoiceConfigOption(
            id          = id,
            title       = title!!,
            description = description,
            choices     = choices.toList(),
            default     = defaultKeys,
            icon        = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            dependency  = dependency,
        )
    }
}

@ConfigOptionBuilderDsl
fun ConfigItemBuilder.multiChoice(block: MultiChoiceConfigBuilder.() -> Unit) {
    add(MultiChoiceConfigBuilder().apply(block))
}
