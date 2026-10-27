package dev.mooner.starlight.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup

class FitColumnLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    var spacing: Int = 0
        set(value) {
            field = value
            requestLayout()
        }

    var fittingCount: Int = 0
        private set

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val available = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> Int.MAX_VALUE
            else                    -> MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        }
        val childWidthSpec = MeasureSpec.makeMeasureSpec(
            (width - paddingLeft - paddingRight).coerceAtLeast(0),
            MeasureSpec.EXACTLY
        )

        var used = 0
        fittingCount = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            child.measure(childWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val needed = child.measuredHeight + if (fittingCount > 0) spacing else 0
            if (fittingCount > 0 && used + needed > available)
                break
            used += needed
            fittingCount++
        }

        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(used + paddingTop + paddingBottom, MeasureSpec.getSize(heightMeasureSpec))
            else                -> used + paddingTop + paddingBottom
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var top = paddingTop
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (index >= fittingCount) {
                child.layout(0, 0, 0, 0)
                continue
            }
            child.layout(paddingLeft, top, paddingLeft + child.measuredWidth, top + child.measuredHeight)
            top += child.measuredHeight + spacing
        }
    }
}
