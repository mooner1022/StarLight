package dev.mooner.starlight.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import dev.mooner.starlight.R
import dev.mooner.starlight.utils.getColorCompat
import kotlin.math.ceil
import kotlin.math.max

class ActivityChartView(context: Context) : View(context) {

    var values: IntArray = IntArray(0)
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val barGap = 2 * density
    private val minBarHeight = 2 * density
    private val minSlotWidth = 5 * density
    private val rect = RectF()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColorCompat(R.color.main_dark)
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColorCompat(R.color.text_sub)
        alpha = 64
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty() || width == 0)
            return
        val groupSize = ceil(values.size / (width / minSlotWidth)).toInt().coerceAtLeast(1)
        val bars = values.toList().chunked(groupSize, List<Int>::sum)
        val maxValue = max(bars.max(), 1)
        val barWidth = (width - barGap * (bars.size - 1)) / bars.size
        val radius = barWidth / 2
        bars.forEachIndexed { index, value ->
            val left = index * (barWidth + barGap)
            val barHeight = if (value == 0) minBarHeight else max(minBarHeight, height * value.toFloat() / maxValue)
            rect.set(left, height - barHeight, left + barWidth, height.toFloat())
            canvas.drawRoundRect(rect, radius, radius, if (value == 0) emptyPaint else barPaint)
        }
    }
}
