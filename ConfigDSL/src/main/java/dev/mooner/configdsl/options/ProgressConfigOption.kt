package dev.mooner.configdsl.options

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.LinearProgressIndicator
import dev.mooner.configdsl.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

data class ProgressState(
    val progress : Int?,
    val max      : Int = 100,
    val label    : String? = null,
) {

    companion object {

        fun indeterminate(label: String? = null) =
            ProgressState(null, label = label)
    }
}

class ProgressConfigOption(
    override val id          : String,
    override val title       : String,
    override val description : String?,
             initialState    : ProgressState,
    override val icon        : IconInfo,
    override val dependency  : String? = null,
): ConfigOption<ProgressConfigOption.ProgressViewHolder, Unit>() {

    override val default: Unit = Unit
    override val hasError: Boolean = false

    @Volatile
    var state: ProgressState = initialState
        private set

    fun update(state: ProgressState): Boolean {
        this.state = state
        return requestViewUpdate()
    }

    override fun onCreateViewHolder(parent: ViewGroup): ProgressViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_progress, parent, false)
        return ProgressViewHolder(view)
    }

    override fun dataToJson(value: Unit): JsonElement =
        JsonNull

    override fun dataFromJson(jsonElement: JsonElement) =
        Unit

    override fun onDraw(holder: ProgressViewHolder, data: Unit) {
        val (progress, max, label) = state
        val text = label ?: progress?.let { "${it * 100 / max.coerceAtLeast(1)}%" }
        holder.value.text = text
        holder.value.isVisible = !text.isNullOrEmpty()
        holder.setIndeterminate(progress == null)
        if (progress != null) {
            holder.progress.max = max
            holder.progress.setProgressCompat(progress.coerceIn(0, max), true)
        }
    }

    class ProgressViewHolder(itemView: View): DefaultViewHolder(itemView) {

        val value   : TextView                = itemView.findViewById(R.id.value)
        val progress: LinearProgressIndicator = itemView.findViewById(R.id.progress)

        internal fun setIndeterminate(indeterminate: Boolean) {
            if (progress.isIndeterminate == indeterminate)
                return
            progress.visibility = View.INVISIBLE
            progress.isIndeterminate = indeterminate
            progress.visibility = View.VISIBLE
        }
    }
}

class ProgressConfigBuilder: ConfigBuilderBase<ProgressConfigOption.ProgressViewHolder, Unit>() {

    var progress: Int? = 0
    var max: Int = 100
    var label: String? = null

    fun bind(flow: Flow<ProgressState>) =
        addBinding { flow.collect { (this as ProgressConfigOption).update(it) } }

    override fun build(): ProgressConfigOption {
        requiredField("title", title)
        require(max > 0) { "Progress '$id' must have a positive max" }

        return ProgressConfigOption(
            id           = id,
            title        = title!!,
            description  = description,
            initialState = ProgressState(progress, max, label),
            icon         = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            dependency   = dependency,
        )
    }
}

fun ConfigItemBuilder.progress(block: ProgressConfigBuilder.() -> Unit) {
    add(ProgressConfigBuilder().apply(block))
}
