package dev.mooner.configdsl.options

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import dev.mooner.configdsl.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class Choice(val key: String, val label: String)

class ChoiceConfigOption(
    override val id          : String,
    override val title       : String,
    override val description : String?,
             val choices     : List<Choice>,
    override val default     : String,
             val onSelected  : ((key: String) -> Unit)?,
    override val icon        : IconInfo,
    override val dependency  : String? = null,
): ConfigOption<SpinnerConfigOption.SpinnerViewHolder, String>() {

    override val hasError: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup): SpinnerConfigOption.SpinnerViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_spinner, parent, false)
        return SpinnerConfigOption.SpinnerViewHolder(view)
    }

    override fun dataToJson(value: String): JsonElement =
        JsonPrimitive(value)

    override fun dataFromJson(jsonElement: JsonElement): String {
        val primitive = jsonElement.jsonPrimitive
        if (!primitive.isString)
            primitive.intOrNull?.let(choices::getOrNull)?.let { return it.key }
        return primitive.content
    }

    override fun onDraw(holder: SpinnerConfigOption.SpinnerViewHolder, data: String) {
        val spinner = holder.spinner
        val selection = choices.indexOfFirst { it.key == data }.coerceAtLeast(0)
        spinner.onItemSelectedListener = null
        spinner.adapter = ArrayAdapter(spinner.context, R.layout.configdsl_spinner_item, choices.map(Choice::label)).apply {
            setDropDownViewResource(R.layout.configdsl_spinner_dropdown_item)
        }
        spinner.setSelection(selection, false)
        holder.selection = selection
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == holder.selection)
                    return
                holder.selection = position
                val key = choices[position].key
                onSelected?.invoke(key)
                notifyUpdated(key)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        holder.itemView.setOnClickListener {
            if (spinner.isEnabled)
                spinner.performClick()
        }
    }
}

class ChoiceConfigBuilder: ConfigBuilderBase<SpinnerConfigOption.SpinnerViewHolder, String>() {

    var defaultKey: String? = null

    private val choices: MutableList<Choice> = arrayListOf()
    private var onSelected: ((key: String) -> Unit)? = null

    fun option(key: String, label: String) {
        choices += Choice(key, label)
    }

    fun setOnSelectedListener(callback: (key: String) -> Unit) {
        onSelected = callback
    }

    override fun build(): ChoiceConfigOption {
        requiredField("title", title)
        require(choices.isNotEmpty()) { "Choice '$id' must have at least one option" }
        val default = defaultKey ?: choices.first().key
        require(choices.any { it.key == default }) { "Default key '$default' of choice '$id' is not one of its options" }

        return ChoiceConfigOption(
            id          = id,
            title       = title!!,
            description = description,
            choices     = choices.toList(),
            default     = default,
            onSelected  = onSelected,
            icon        = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            dependency  = dependency,
        )
    }
}

fun ConfigItemBuilder.choice(block: ChoiceConfigBuilder.() -> Unit) {
    add(ChoiceConfigBuilder().apply(block))
}
