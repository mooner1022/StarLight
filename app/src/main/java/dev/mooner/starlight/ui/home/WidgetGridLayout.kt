package dev.mooner.starlight.ui.home

import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.widget.NestedScrollView
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.widget.GridPlacement
import dev.mooner.starlight.plugincore.widget.WidgetGridPacker
import dev.mooner.starlight.plugincore.widget.WidgetSpan
import dev.mooner.starlight.plugincore.widget.WidgetSpanSpec
import dev.mooner.starlight.ui.home.WidgetEditDecoration.Handle
import dev.mooner.starlight.utils.getColorCompat
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

class WidgetGridLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    interface Listener {
        fun onEditModeRequested(view: View)
        fun onSelectionChanged(view: View?)
        fun onSpanChanged(view: View, span: WidgetSpan)
        fun onOrderChanged()
        fun onEmptyAreaTapped()
    }

    class LayoutParams(
        val spec: WidgetSpanSpec,
        var span: WidgetSpan?,
        val isAutoHeight: Boolean,
    ) : ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT) {

        var resolvedSpan: WidgetSpan = spec.default
            internal set

        internal var contentHeight: Int = 0
    }

    private sealed interface Gesture
    private class Pending(val view: View, val downX: Float, val downY: Float) : Gesture
    private class Dragging(val view: View, val offsetX: Float, val offsetY: Float) : Gesture {
        var lastTarget: View? = null
    }
    private class Resizing(val view: View, val handle: Handle) : Gesture
    private class EmptyTap(val downX: Float, val downY: Float) : Gesture

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handleTouchRadius = 24 * density
    private val autoScrollEdge = 64 * density
    private val autoScrollStep = (12 * density).roundToInt()

    var columns: Int = 4
        set(value) {
            field = value
            requestLayout()
        }

    var cellHeight: Int = (54 * density).roundToInt()
        set(value) {
            field = value
            requestLayout()
        }

    var gap: Int = (10 * density).roundToInt()
        set(value) {
            field = value
            requestLayout()
        }

    var listener: Listener? = null

    var isEditing: Boolean = false
        private set

    var selected: View? = null
        private set

    private val order: MutableList<View> = mutableListOf()
    val widgets: List<View>
        get() = order

    private var cellWidth = 0f
    private var placements: List<GridPlacement> = emptyList()

    private var gesture: Gesture? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var longPressTarget: View? = null
    private var longPressDown: PointF? = null

    private val longPressRunnable = Runnable {
        val target = longPressTarget ?: return@Runnable
        val down = longPressDown ?: return@Runnable
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        setEditing(true)
        cancelChildTouches()
        select(target)
        gesture = Pending(target, down.x, down.y)
        listener?.onEditModeRequested(target)
    }

    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            val dragging = gesture as? Dragging ?: return
            val scroll = scrollParent ?: return
            val dy = autoScrollDelta(scroll)
            if (dy == 0)
                return
            val before = scroll.scrollY
            scroll.scrollBy(0, dy)
            val moved = scroll.scrollY - before
            if (moved == 0)
                return
            lastTouchY += moved
            updateDrag(dragging)
            postOnAnimation(this)
        }
    }

    private val scrollParent: NestedScrollView?
        get() = generateSequence(parent) { it.parent }
            .filterIsInstance<NestedScrollView>()
            .firstOrNull()

    private val View.gridParams: LayoutParams
        get() = layoutParams as LayoutParams

    fun addWidget(view: View, spec: WidgetSpanSpec, span: WidgetSpan?, isAutoHeight: Boolean) {
        view.layoutParams = LayoutParams(spec, span, isAutoHeight)
        order += view
        addView(view)
        applyDecoration(view)
    }

    fun removeWidget(view: View) {
        if (selected === view)
            select(null)
        order -= view
        removeView(view)
    }

    fun spanOf(view: View): WidgetSpan? =
        view.gridParams.span

    fun resolvedSpanOf(view: View): WidgetSpan =
        view.gridParams.resolvedSpan

    fun setEditing(editing: Boolean) {
        if (isEditing == editing)
            return
        isEditing = editing
        if (!editing) {
            cancelGesture()
            select(null)
        }
        order.forEach(::applyDecoration)
    }

    fun select(view: View?) {
        if (selected === view)
            return
        selected = view
        order.forEach(::applyDecoration)
        listener?.onSelectionChanged(view)
    }

    private fun applyDecoration(view: View) {
        if (!isEditing) {
            view.foreground = null
            return
        }
        val decoration = view.foreground as? WidgetEditDecoration
            ?: WidgetEditDecoration(
                density = density,
                cornerRadius = resources.getDimension(R.dimen.card_radius),
                accentColor = context.getColorCompat(R.color.main_dark),
                outlineColor = context.getColorCompat(R.color.text_sub),
                handleStrokeColor = context.getColorCompat(R.color.cardview),
            ).also { view.foreground = it }
        decoration.isSelected = view === selected
        decoration.handles = handlesOf(view)
    }

    private fun handlesOf(view: View): Set<Handle> {
        val params = view.gridParams
        val maxX = minOf(params.spec.max.x, columns)
        val horizontal = minOf(params.spec.min.x, maxX) < maxX
        val vertical = !params.isAutoHeight && params.spec.min.y < params.spec.max.y
        return buildSet {
            if (horizontal && vertical)
                add(Handle.CORNER)
            if (horizontal)
                add(Handle.RIGHT)
            if (vertical)
                add(Handle.BOTTOM)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val innerWidth = width - paddingLeft - paddingRight
        cellWidth = (innerWidth - gap * (columns - 1)).coerceAtLeast(0).toFloat() / columns

        val spans = order.map { child ->
            val params = child.gridParams
            val span = params.spec.resolve(params.span, columns)
            if (params.isAutoHeight) {
                child.measure(exactly(spanWidth(span.x)), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                params.contentHeight = child.measuredHeight
                val rows = ceil((child.measuredHeight + gap / 2).toFloat() / (cellHeight + gap)).toInt()
                WidgetSpan(span.x, rows.coerceAtLeast(1))
            } else {
                span
            }
        }
        placements = WidgetGridPacker.pack(spans, columns)
        order.forEachIndexed { index, child ->
            val span = placements[index].span
            val params = child.gridParams
            params.resolvedSpan = span
            val height = if (params.isAutoHeight)
                maxOf(spanHeight(span.y), params.contentHeight)
            else
                spanHeight(span.y)
            child.measure(exactly(spanWidth(span.x)), exactly(height))
        }

        val rows = WidgetGridPacker.rowCount(placements)
        val height = paddingTop + paddingBottom + if (rows == 0) 0 else spanHeight(rows)
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        order.forEachIndexed { index, child ->
            val cell = placements.getOrNull(index)?.cell ?: return@forEachIndexed
            val left = paddingLeft + ((cellWidth + gap) * cell.column).roundToInt()
            val top = paddingTop + (cellHeight + gap) * cell.row
            val wasLaidOut = child.isLaidOut
            val dx = child.left - left
            val dy = child.top - top
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
            if (!wasLaidOut || (dx == 0 && dy == 0))
                return@forEachIndexed
            child.translationX += dx
            child.translationY += dy
            if (child !== (gesture as? Dragging)?.view)
                child.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
        }
    }

    private fun spanWidth(x: Int): Int =
        (cellWidth * x + gap * (x - 1)).roundToInt()

    private fun spanHeight(y: Int): Int =
        cellHeight * y + gap * (y - 1)

    private fun exactly(size: Int): Int =
        MeasureSpec.makeMeasureSpec(size.coerceAtLeast(0), MeasureSpec.EXACTLY)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isEditing)
            trackLongPress(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        isEditing

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isEditing)
            return ev.actionMasked == MotionEvent.ACTION_DOWN && findChildAt(ev.x, ev.y) != null

        lastTouchX = ev.x
        lastTouchY = ev.y
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val selected = selected
                val handle = selected?.let { hitHandle(it, ev.x, ev.y) }
                gesture = if (selected != null && handle != null) {
                    Resizing(selected, handle)
                } else {
                    findChildAt(ev.x, ev.y)
                        ?.let { child ->
                            select(child)
                            Pending(child, ev.x, ev.y)
                        }
                        ?: EmptyTap(ev.x, ev.y)
                }
                if (gesture !is EmptyTap)
                    parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> when (val current = gesture) {
                is Pending -> if (hypot(ev.x - current.downX, ev.y - current.downY) > touchSlop)
                    startDrag(current.view)
                is Dragging -> {
                    updateDrag(current)
                    updateAutoScroll()
                }
                is Resizing -> updateResize(current)
                is EmptyTap -> if (hypot(ev.x - current.downX, ev.y - current.downY) > touchSlop)
                    gesture = null
                null -> {}
            }
            MotionEvent.ACTION_UP -> {
                val current = gesture
                cancelGesture()
                if (current is EmptyTap)
                    listener?.onEmptyAreaTapped()
            }
            MotionEvent.ACTION_CANCEL ->
                cancelGesture()
        }
        return true
    }

    private fun trackLongPress(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelLongPressTracking()
                val target = findChildAt(ev.x, ev.y) ?: return
                longPressTarget = target
                longPressDown = PointF(ev.x, ev.y)
                postDelayed(longPressRunnable, (ViewConfiguration.getLongPressTimeout() * LONG_PRESS_RATIO).toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val down = longPressDown ?: return
                if (hypot(ev.x - down.x, ev.y - down.y) > touchSlop)
                    cancelLongPressTracking()
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_DOWN ->
                cancelLongPressTracking()
        }
    }

    private fun cancelChildTouches() {
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    private fun cancelLongPressTracking() {
        removeCallbacks(longPressRunnable)
        longPressTarget = null
        longPressDown = null
    }

    private fun cancelGesture() {
        (gesture as? Dragging)?.let(::endDrag)
        gesture = null
        removeCallbacks(autoScrollRunnable)
    }

    private fun startDrag(view: View) {
        view.animate().cancel()
        gesture = Dragging(
            view = view,
            offsetX = lastTouchX - (view.left + view.translationX),
            offsetY = lastTouchY - (view.top + view.translationY),
        )
        view.animate()
            .scaleX(1.03f)
            .scaleY(1.03f)
            .translationZ(8 * density)
            .setDuration(120)
            .start()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun updateDrag(dragging: Dragging) {
        val view = dragging.view
        view.translationX = lastTouchX - dragging.offsetX - view.left
        view.translationY = lastTouchY - dragging.offsetY - view.top

        val target = order.firstOrNull { it !== view && it.containsLaidOut(lastTouchX, lastTouchY) }
        if (target == null || target === dragging.lastTarget) {
            dragging.lastTarget = target
            return
        }
        dragging.lastTarget = target
        val targetIndex = order.indexOf(target)
        order.remove(view)
        order.add(targetIndex, view)
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        requestLayout()
        listener?.onOrderChanged()
    }

    private fun endDrag(dragging: Dragging) {
        dragging.view.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .translationZ(0f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun updateResize(resizing: Resizing) {
        val view = resizing.view
        val params = view.gridParams
        val current = params.resolvedSpan
        var spanX = current.x
        var spanY = current.y
        if (resizing.handle != Handle.BOTTOM)
            spanX = ((lastTouchX - view.left + gap) / (cellWidth + gap)).roundToInt()
        if (resizing.handle != Handle.RIGHT && !params.isAutoHeight)
            spanY = ((lastTouchY - view.top + gap) / (cellHeight + gap)).roundToInt()

        val next = params.spec.resolve(WidgetSpan(spanX.coerceAtLeast(1), spanY.coerceAtLeast(1)), columns)
        if (next.x == current.x && (params.isAutoHeight || next.y == current.y))
            return
        params.span = next
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        requestLayout()
        listener?.onSpanChanged(view, next)
    }

    private fun updateAutoScroll() {
        removeCallbacks(autoScrollRunnable)
        val scroll = scrollParent ?: return
        if (autoScrollDelta(scroll) != 0)
            postOnAnimation(autoScrollRunnable)
    }

    private fun autoScrollDelta(scroll: NestedScrollView): Int {
        val gridLocation = IntArray(2).also(::getLocationInWindow)
        val scrollLocation = IntArray(2).also(scroll::getLocationInWindow)
        val y = gridLocation[1] + lastTouchY - scrollLocation[1]
        return when {
            y < autoScrollEdge                 -> -autoScrollStep
            y > scroll.height - autoScrollEdge -> autoScrollStep
            else                               -> 0
        }
    }

    private fun hitHandle(view: View, x: Float, y: Float): Handle? {
        val decoration = view.foreground as? WidgetEditDecoration ?: return null
        return decoration.handles.firstOrNull { handle ->
            val center = decoration.handleCenter(handle, view.width, view.height)
            hypot(
                x - (view.left + view.translationX + center.x),
                y - (view.top + view.translationY + center.y)
            ) <= handleTouchRadius
        }
    }

    private fun findChildAt(x: Float, y: Float): View? =
        order.lastOrNull { child ->
            x >= child.left + child.translationX && x < child.right + child.translationX &&
                    y >= child.top + child.translationY && y < child.bottom + child.translationY
        }

    private fun View.containsLaidOut(x: Float, y: Float): Boolean =
        x >= left && x < right && y >= top && y < bottom

    override fun onDetachedFromWindow() {
        cancelLongPressTracking()
        removeCallbacks(autoScrollRunnable)
        super.onDetachedFromWindow()
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean =
        p is LayoutParams

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams =
        LayoutParams(WidgetSpanSpec(WidgetSpan(columns, 1)), null, isAutoHeight = false)

    private companion object {
        const val LONG_PRESS_RATIO = 0.8f
    }
}
