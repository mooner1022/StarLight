package dev.mooner.configdsl.options

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import dev.mooner.configdsl.*
import dev.mooner.configdsl.utils.copyToClipboard
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

class TextListConfigOption(
    override val id          : String,
    override val title       : String,
    override val description : String?,
             initialLines    : List<String>,
             val monospace   : Boolean,
             val copyable    : Boolean,
             val maxVisible  : Int,
             val emptyText   : String?,
    override val icon        : IconInfo,
    override val dependency  : String? = null,
): ConfigOption<TextListConfigOption.TextListViewHolder, Unit>() {

    override val default: Unit = Unit
    override val hasError: Boolean = false

    @Volatile
    var lines: List<String> = initialLines
        private set

    private var isExpanded = false

    fun update(lines: List<String>): Boolean {
        this.lines = lines
        return requestViewUpdate()
    }

    override fun onCreateViewHolder(parent: ViewGroup): TextListViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_text_list, parent, false)
        return TextListViewHolder(view)
    }

    override fun dataToJson(value: Unit): JsonElement =
        JsonNull

    override fun dataFromJson(jsonElement: JsonElement) =
        Unit

    override fun onDraw(holder: TextListViewHolder, data: Unit) {
        val context = holder.context
        val lines = lines
        val isCollapsible = lines.size > maxVisible
        val visible = if (isCollapsible && !isExpanded) lines.take(maxVisible) else lines

        holder.count.text = context.getString(R.string.configdsl_item_count, lines.size)
        holder.lines.removeAllViews()
        if (lines.isEmpty())
            holder.lines.addView(lineView(context, emptyText ?: context.getString(R.string.configdsl_list_empty), isPlaceholder = true))
        for (line in visible)
            holder.lines.addView(lineView(context, line, isPlaceholder = false))

        holder.more.isVisible = isCollapsible
        holder.more.text = if (isExpanded)
            context.getString(R.string.configdsl_show_less)
        else
            context.getString(R.string.configdsl_show_more, lines.size - visible.size)
        holder.more.setOnClickListener {
            isExpanded = !isExpanded
            requestViewUpdate()
        }
    }

    private fun lineView(context: Context, text: String, isPlaceholder: Boolean): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(context.getColor(if (isPlaceholder) R.color.text_sub else R.color.text))
            typeface = if (monospace && !isPlaceholder)
                Typeface.MONOSPACE
            else
                ResourcesCompat.getFont(context, R.font.nanumsquare_round_regular)
            val horizontal = (12 * resources.displayMetrics.density).toInt()
            val vertical = (6 * resources.displayMetrics.density).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (copyable && !isPlaceholder) {
                val ripple = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                setBackgroundResource(ripple.resourceId)
                setOnLongClickListener {
                    context.copyToClipboard(displayTitle, text)
                    true
                }
            }
        }

    class TextListViewHolder(itemView: View): DefaultViewHolder(itemView) {

        val count: TextView     = itemView.findViewById(R.id.count)
        val lines: LinearLayout = itemView.findViewById(R.id.lines)
        val more : TextView     = itemView.findViewById(R.id.more)
    }
}

class TextListConfigBuilder: ConfigBuilderBase<TextListConfigOption.TextListViewHolder, Unit>() {

    var lines: List<String> = emptyList()
    var monospace: Boolean = false
    var copyable: Boolean = false
    var maxVisible: Int = 5
    var emptyText: String? = null

    fun bind(flow: Flow<List<String>>) =
        addBinding { flow.collect { (this as TextListConfigOption).update(it) } }

    override fun build(): TextListConfigOption {
        requiredField("title", title)
        require(maxVisible > 0) { "Text list '$id' must show at least one line" }

        return TextListConfigOption(
            id           = id,
            title        = title!!,
            description  = description,
            initialLines = lines,
            monospace    = monospace,
            copyable     = copyable,
            maxVisible   = maxVisible,
            emptyText    = emptyText,
            icon         = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            dependency   = dependency,
        )
    }
}

@ConfigOptionBuilderDsl
fun ConfigItemBuilder.textList(block: TextListConfigBuilder.() -> Unit) {
    add(TextListConfigBuilder().apply(block))
}
