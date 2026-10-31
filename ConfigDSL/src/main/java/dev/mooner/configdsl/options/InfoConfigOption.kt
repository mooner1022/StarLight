package dev.mooner.configdsl.options

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.CircularProgressIndicator
import dev.mooner.configdsl.*
import dev.mooner.configdsl.utils.copyToClipboard
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlin.time.Duration

enum class OptionStatus(@ColorRes val colorRes: Int) {
    OK(R.color.configdsl_status_ok),
    WARNING(R.color.configdsl_status_warning),
    ERROR(R.color.configdsl_status_error),
    LOADING(R.color.text_sub);

    val isAlert: Boolean
        get() = this == WARNING || this == ERROR
}

data class InfoState(val value: String?, val status: OptionStatus? = null)

class InfoConfigOption(
    override val id          : String,
    override val title       : String,
    override val description : String?,
             val copyable    : Boolean,
             val onClick     : ButtonOnClickListener?,
             initialState    : InfoState,
    override val icon        : IconInfo,
    override val dependency  : String? = null,
): ConfigOption<InfoConfigOption.InfoViewHolder, Unit>() {

    override val default: Unit = Unit
    override val hasError: Boolean = false

    @Volatile
    var state: InfoState = initialState
        private set

    fun update(state: InfoState): Boolean {
        this.state = state
        return requestViewUpdate()
    }

    fun updateValue(value: String?): Boolean =
        update(state.copy(value = value))

    override fun onCreateViewHolder(parent: ViewGroup): InfoViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_info, parent, false)
        return InfoViewHolder(view)
    }

    override fun dataToJson(value: Unit): JsonElement =
        JsonNull

    override fun dataFromJson(jsonElement: JsonElement) =
        Unit

    override fun onDraw(holder: InfoViewHolder, data: Unit) {
        val (value, status) = state
        val context = holder.context
        val isLoading = status == OptionStatus.LOADING

        holder.value.text = value
        holder.value.isVisible = !value.isNullOrEmpty() && !isLoading
        holder.value.setTextColor(context.getColor(status?.takeIf(OptionStatus::isAlert)?.colorRes ?: R.color.text_sub))
        holder.progress.isVisible = isLoading
        if (status != null && !isLoading)
            holder.icon.imageTintList = ColorStateList.valueOf(context.getColor(status.colorRes))

        holder.itemView.setOnClickListener(onClick?.let { listener -> View.OnClickListener { listener(it) } })
        holder.itemView.setOnLongClickListener(
            if (copyable) View.OnLongClickListener { copyValue(context) } else null
        )
        holder.itemView.isClickable = onClick != null
        holder.itemView.isLongClickable = copyable
        holder.itemView.background = if (onClick != null || copyable) holder.ripple else null
    }

    private fun copyValue(context: Context): Boolean {
        val text = state.value ?: displayDescription ?: return false
        context.copyToClipboard(displayTitle, text)
        return true
    }

    class InfoViewHolder(itemView: View): DefaultViewHolder(itemView) {

        val value   : TextView                  = itemView.findViewById(R.id.value)
        val progress: CircularProgressIndicator = itemView.findViewById(R.id.progress)

        internal val ripple: Drawable? = itemView.background
    }
}

class InfoConfigBuilder: ConfigBuilderBase<InfoConfigOption.InfoViewHolder, Unit>() {

    var value: String? = null
    var status: OptionStatus? = null
    var copyable: Boolean = false

    private var onClick: ButtonOnClickListener? = null

    fun setOnClickListener(callback: ButtonOnClickListener) {
        onClick = callback
    }

    fun bind(flow: Flow<InfoState>) =
        addBinding { flow.collect { (this as InfoConfigOption).update(it) } }

    fun bindValue(flow: Flow<String?>) =
        addBinding { flow.collect { (this as InfoConfigOption).updateValue(it) } }

    fun bindValue(every: Duration, block: suspend () -> String?) =
        bindValue(tickerFlow(every, block))

    override fun build(): InfoConfigOption {
        requiredField("title", title)

        return InfoConfigOption(
            id           = id,
            title        = title!!,
            description  = description,
            copyable     = copyable,
            onClick      = onClick,
            initialState = InfoState(value, status),
            icon         = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            dependency   = dependency,
        )
    }
}

fun ConfigItemBuilder.info(block: InfoConfigBuilder.() -> Unit) {
    add(InfoConfigBuilder().apply(block))
}
