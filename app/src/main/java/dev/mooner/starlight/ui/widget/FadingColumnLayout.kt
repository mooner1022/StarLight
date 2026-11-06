package dev.mooner.starlight.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import kotlin.math.roundToInt

class FadingColumnLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    var spacing: Int = 0
        set(value) {
            field = value
            requestLayout()
        }

    private var contentHeight = 0

    private val isOverflowing: Boolean
        get() = contentHeight > height - paddingTop - paddingBottom

    init {
        setWillNotDraw(false)
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength((FADE_LENGTH_DP * resources.displayMetrics.density).roundToInt())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val childWidthSpec = MeasureSpec.makeMeasureSpec(
            (width - paddingLeft - paddingRight).coerceAtLeast(0),
            MeasureSpec.EXACTLY
        )

        contentHeight = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            child.measure(childWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            contentHeight += child.measuredHeight + if (index > 0) spacing else 0
        }

        val wanted = contentHeight + paddingTop + paddingBottom
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(wanted, MeasureSpec.getSize(heightMeasureSpec))
            else                -> wanted
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val bottom = b - t - paddingBottom
        var top = paddingTop
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (top >= bottom) {
                child.layout(0, 0, 0, 0)
                continue
            }
            child.layout(paddingLeft, top, paddingLeft + child.measuredWidth, top + child.measuredHeight)
            top += child.measuredHeight + spacing
        }
    }

    override fun getTopFadingEdgeStrength(): Float =
        0f

    override fun getBottomFadingEdgeStrength(): Float =
        if (isOverflowing) 1f else 0f

    private companion object {
        const val FADE_LENGTH_DP = 36
    }
}
