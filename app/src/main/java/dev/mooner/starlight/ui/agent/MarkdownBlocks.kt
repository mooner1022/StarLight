package dev.mooner.starlight.ui.agent

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import dev.mooner.starlight.R
import io.noties.markwon.Markwon

/**
 * Markdown split into prose and GitHub-style tables. Markwon's core has no tables, and a table squeezed into the
 * width of a phone is unreadable anyway, so tables get their own view that scrolls sideways.
 */
internal object MarkdownBlocks {

    sealed class Block {
        data class Text(val markdown: String) : Block()
        data class Table(val header: List<String>, val aligns: List<Int>, val rows: List<List<String>>) : Block()
    }

    private val DELIMITER = Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$")
    private val FENCE = Regex("^\\s*(```|~~~)")

    fun split(markdown: String): List<Block> {
        val lines = markdown.lines()
        val blocks = mutableListOf<Block>()
        val text = StringBuilder()
        var inFence = false
        var i = 0
        fun flushText() {
            if (text.isNotBlank())
                blocks += Block.Text(text.toString().trimEnd('\n'))
            text.setLength(0)
        }
        while (i < lines.size) {
            val line = lines[i]
            if (FENCE.containsMatchIn(line))
                inFence = !inFence
            val next = lines.getOrNull(i + 1)
            if (!inFence && line.contains('|') && next != null && DELIMITER.matches(next)) {
                val header = cells(line)
                val delimiter = cells(next)
                if (header.isNotEmpty() && header.size == delimiter.size) {
                    flushText()
                    val rows = mutableListOf<List<String>>()
                    var j = i + 2
                    while (j < lines.size && lines[j].contains('|') && lines[j].isNotBlank()) {
                        rows += cells(lines[j]).let { row -> List(header.size) { row.getOrElse(it) { "" } } }
                        j++
                    }
                    blocks += Block.Table(header, delimiter.map(::alignOf), rows)
                    i = j
                    continue
                }
            }
            text.append(line).append('\n')
            i++
        }
        flushText()
        return blocks
    }

    /** The cells of a table row; `\|` is a pipe inside a cell. */
    private fun cells(line: String): List<String> {
        var row = line.trim()
        if (row.startsWith("|"))
            row = row.substring(1)
        if (row.endsWith("|") && !row.endsWith("\\|"))
            row = row.dropLast(1)
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var index = 0
        while (index < row.length) {
            val c = row[index]
            when {
                c == '\\' && row.getOrNull(index + 1) == '|' -> {
                    cell.append('|')
                    index++
                }
                c == '|' -> {
                    cells += cell.toString().trim()
                    cell.setLength(0)
                }
                else -> cell.append(c)
            }
            index++
        }
        cells += cell.toString().trim()
        return cells
    }

    private fun alignOf(delimiter: String): Int {
        val left = delimiter.startsWith(":")
        val right = delimiter.endsWith(":")
        return when {
            left && right -> Gravity.CENTER_HORIZONTAL
            right         -> Gravity.END
            else          -> Gravity.START
        }
    }
}

/** Renders Markdown into a vertical container, reusing the views already there when the shape stays the same. */
internal class MarkdownRenderer(private val context: Context, private val markwon: Markwon) {

    private val font = ResourcesCompat.getFont(context, R.font.wantedsans_regular)
    private val boldFont = ResourcesCompat.getFont(context, R.font.wantedsans_bold)

    fun render(container: LinearLayout, markdown: String, onLongClick: (() -> Unit)? = null) {
        val blocks = MarkdownBlocks.split(markdown)
        val reusable = container.childCount == blocks.size &&
            blocks.indices.all { (blocks[it] is MarkdownBlocks.Block.Text) == (container.getChildAt(it) is TextView) }
        if (!reusable)
            container.removeAllViews()
        val longClick = onLongClick?.let { View.OnLongClickListener { _ -> it(); true } }
        container.setOnLongClickListener(longClick)
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkdownBlocks.Block.Text -> {
                    val view = if (reusable) container.getChildAt(index) as TextView else textView().also(container::addView)
                    if (view.tag != block) {
                        markwon.setMarkdown(view, block.markdown)
                        view.tag = block
                    }
                    view.setOnLongClickListener(longClick)
                }
                is MarkdownBlocks.Block.Table -> {
                    // A reply streams in many times a second; rebuild a table only when it changed.
                    if (reusable && container.getChildAt(index).tag == block)
                        return@forEachIndexed
                    if (reusable)
                        container.removeViewAt(index)
                    container.addView(tableView(block).apply { tag = block }, index)
                }
            }
        }
    }

    private fun textView() = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        typeface = font
        textSize = 15f
        setLineSpacing(AgentUi.dp(context, 4).toFloat(), 1f)
        setTextColor(ContextCompat.getColor(context, R.color.agent_text))
        setLinkTextColor(ContextCompat.getColor(context, R.color.agent_accent))
        movementMethod = LinkMovementMethod.getInstance()
    }

    private fun tableView(table: MarkdownBlocks.Block.Table): View {
        val divider = ContextCompat.getDrawable(context, R.drawable.divider_agent_table)
        val tableLayout = TableLayout(context).apply {
            setBackgroundResource(R.drawable.bg_agent_table)
            clipToOutline = true
            // Inside the border, so the header's fill doesn't cover it.
            val inset = AgentUi.dp(context, 1)
            setPadding(inset, inset, inset, inset)
            // A narrow table fills the width; a wide one keeps its size and scrolls.
            isStretchAllColumns = true
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerDrawable = divider
        }
        fun row(cells: List<String>, header: Boolean) = TableRow(context).apply {
            if (header)
                setBackgroundColor(ContextCompat.getColor(context, R.color.agent_surface_alt))
            cells.forEachIndexed { column, cell ->
                addView(TextView(context).apply {
                    if (column < cells.lastIndex)
                        setBackgroundResource(R.drawable.bg_agent_table_cell)
                    typeface = if (header) boldFont else font
                    textSize = 13.5f
                    maxWidth = AgentUi.dp(context, MAX_CELL_WIDTH_DP)
                    gravity = table.aligns.getOrElse(column) { Gravity.START }
                    setTextColor(ContextCompat.getColor(context, R.color.agent_text))
                    setPadding(AgentUi.dp(context, 12), AgentUi.dp(context, 8), AgentUi.dp(context, 12), AgentUi.dp(context, 8))
                    markwon.setMarkdown(this, cell.ifEmpty { " " })
                })
            }
        }
        tableLayout.addView(row(table.header, header = true))
        for (cells in table.rows)
            tableLayout.addView(row(cells, header = false))
        return HorizontalScrollView(context).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = AgentUi.dp(context, 6)
                bottomMargin = AgentUi.dp(context, 6)
            }
            addView(tableLayout)
        }
    }

    private companion object {
        const val MAX_CELL_WIDTH_DP = 240
    }
}
