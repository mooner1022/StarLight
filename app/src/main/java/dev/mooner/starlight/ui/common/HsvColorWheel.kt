package dev.mooner.starlight.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * A hue and saturation wheel: the angle is the hue, the distance from the center the saturation. Brightness is set
 * apart, through [value], and darkens the whole wheel.
 */
class HsvColorWheel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Called as the user drags, with the hue (0–360) and saturation (0–1) under the finger. */
    var onPick: (hue: Float, saturation: Float) -> Unit = { _, _ -> }

    var hue: Float = 0f
        private set
    var saturation: Float = 0f
        private set

    /** Brightness, 0–1; the wheel is drawn at it. */
    var value: Float = 1f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        color = Color.WHITE
    }
    private val thumbEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1 * density
        color = 0x66000000
    }
    private val thumbRadius = 11 * density

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    fun setColor(hue: Float, saturation: Float, value: Float) {
        this.hue = hue
        this.saturation = saturation.coerceIn(0f, 1f)
        this.value = value
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Square, as wide as it may be, up to its height.
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
            width
        else
            MeasureSpec.getSize(heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        centerX = w / 2f
        centerY = h / 2f
        radius = min(w, h) / 2f - thumbRadius - thumbRing.strokeWidth
        val hues = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f % 360f, 1f, 1f)) }
        val sweep = SweepGradient(centerX, centerY, hues, null)
        val toWhite = RadialGradient(centerX, centerY, radius.coerceAtLeast(1f), Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP)
        wheelPaint.shader = ComposeShader(sweep, toWhite, PorterDuff.Mode.SRC_OVER)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(centerX, centerY, radius, wheelPaint)
        shadePaint.color = Color.argb(((1 - value) * 255).toInt(), 0, 0, 0)
        canvas.drawCircle(centerX, centerY, radius, shadePaint)

        val angle = Math.toRadians(hue.toDouble())
        val x = centerX + (cos(angle) * saturation * radius).toFloat()
        val y = centerY + (sin(angle) * saturation * radius).toFloat()
        thumbFill.color = Color.HSVToColor(floatArrayOf(hue, saturation, value))
        canvas.drawCircle(x, y, thumbRadius, thumbFill)
        canvas.drawCircle(x, y, thumbRadius, thumbRing)
        canvas.drawCircle(x, y, thumbRadius + thumbRing.strokeWidth / 2, thumbEdge)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Keep the sheet and its scroll from taking the drag.
                parent?.requestDisallowInterceptTouchEvent(true)
                pickAt(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE ->
                pickAt(event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun pickAt(x: Float, y: Float) {
        val dx = x - centerX
        val dy = y - centerY
        hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360) % 360).toFloat()
        saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
        invalidate()
        onPick(hue, saturation)
    }
}
