package dev.mooner.starlight.ui.splash.intro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils
import dev.mooner.starlight.R
import dev.mooner.starlight.ui.plugins.store.addSparkle
import kotlin.math.abs

class IntroPageIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val goldColor = context.getColor(R.color.store_gold)
    private val idleColor = ColorUtils.setAlphaComponent(context.getColor(R.color.store_star), 90)

    private val dotRadius = dp(3f)
    private val sparkleRadius = dp(8f)
    private val spacing = dp(22f)

    var count: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var progress: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (count == 0)
            return
        val start = width / 2f - spacing * (count - 1) / 2f
        val cy = height / 2f
        for (i in 0 until count) {
            val cx = start + spacing * i
            val weight = (1f - abs(progress - i)).coerceIn(0f, 1f)
            if (weight < 1f) {
                paint.color = idleColor
                paint.alpha = (Color.alpha(idleColor) * (1f - weight)).toInt()
                canvas.drawCircle(cx, cy, dotRadius, paint)
            }
            if (weight > 0f) {
                paint.color = goldColor
                paint.alpha = (255 * weight).toInt()
                path.reset()
                path.addSparkle(cx, cy, dotRadius + (sparkleRadius - dotRadius) * weight)
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
