package dev.mooner.starlight.ui.agent

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.core.graphics.ColorUtils
import dev.mooner.starlight.R

/** Colors the unified-diff text of [dev.mooner.starlight.agent.tools.TextDiff]: added lines green, removed red. */
internal object DiffText {

    fun render(context: Context, diff: String): CharSequence {
        val added = context.getColor(R.color.agent_ok)
        val removed = context.getColor(R.color.agent_error)
        val faint = context.getColor(R.color.agent_text_faint)
        val out = SpannableStringBuilder()
        val lines = diff.lines()
        lines.forEachIndexed { index, line ->
            val start = out.length
            out.append(line)
            val end = out.length
            if (index < lines.lastIndex)
                out.append('\n')
            val (text, background) = when {
                line.startsWith("@@") || line.startsWith("…") -> faint to null
                line.startsWith("+")                          -> added to ColorUtils.setAlphaComponent(added, BACKGROUND_ALPHA)
                line.startsWith("-")                          -> removed to ColorUtils.setAlphaComponent(removed, BACKGROUND_ALPHA)
                else                                          -> null to null
            }
            if (text != null)
                out.setSpan(ForegroundColorSpan(text), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (background != null)
                out.setSpan(BackgroundColorSpan(background), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    private const val BACKGROUND_ALPHA = 0x24
}
