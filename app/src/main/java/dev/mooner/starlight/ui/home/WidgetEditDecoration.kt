package dev.mooner.starlight.ui.home

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable

class WidgetEditDecoration(
    private val density: Float,
    private val cornerRadius: Float,
    private val accentColor: Int,
    private val outlineColor: Int,
    private val handleStrokeColor: Int,
) : Drawable() {

    enum class Handle { RIGHT, BOTTOM, CORNER }

    var isSelected: Boolean = false
        set(value) {
            field = value
            invalidateSelf()
        }

    var handles: Set<Handle> = emptySet()
        set(value) {
            field = value
            invalidateSelf()
        }

    private val strokeWidth = 2 * density
    private val handleInset = 10 * density
    private val handleLength = 22 * density
    private val handleThickness = 6 * density
    private val rect = RectF()

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@WidgetEditDecoration.strokeWidth
    }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = handleStrokeColor
    }
    private val dash = DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = accentColor
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = handleStrokeColor
    }

    override fun draw(canvas: Canvas) {
        val half = strokeWidth / 2
        rect.set(bounds)
        rect.inset(half, half)
        outlinePaint.color = if (isSelected) accentColor else outlineColor
        outlinePaint.pathEffect = if (isSelected) null else dash
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, outlinePaint)

        val inset = strokeWidth / 2 + innerPaint.strokeWidth / 2
        rect.inset(inset, inset)
        innerPaint.pathEffect = outlinePaint.pathEffect
        canvas.drawRoundRect(rect, cornerRadius - inset, cornerRadius - inset, innerPaint)

        if (!isSelected)
            return
        for (handle in handles) {
            val center = handleCenter(handle, bounds.width(), bounds.height())
            val (w, h) = when (handle) {
                Handle.RIGHT  -> handleThickness to handleLength
                Handle.BOTTOM -> handleLength to handleThickness
                Handle.CORNER -> handleThickness * 2 to handleThickness * 2
            }
            rect.set(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2)
            canvas.drawRoundRect(rect, w, w, handlePaint)
            canvas.drawRoundRect(rect, w, w, handleStrokePaint)
        }
    }

    fun handleCenter(handle: Handle, width: Int, height: Int): PointF =
        when (handle) {
            Handle.RIGHT  -> PointF(width - handleInset, height / 2f)
            Handle.BOTTOM -> PointF(width / 2f, height - handleInset)
            Handle.CORNER -> PointF(width - handleInset, height - handleInset)
        }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int =
        PixelFormat.TRANSLUCENT
}
