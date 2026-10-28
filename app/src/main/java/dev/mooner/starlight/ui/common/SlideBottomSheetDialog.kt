package dev.mooner.starlight.ui.common

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.view.doOnLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dev.mooner.starlight.R
import kotlin.math.roundToInt

open class SlideBottomSheetDialog(context: Context) : BottomSheetDialog(context, R.style.ThemeOverlay_StarLight_SlideBottomSheet) {

    private var sheetAnimator: ValueAnimator? = null
    private var isDismissing = false
    private var isTracking = false

    private val sheet: View?
        get() = findViewById(com.google.android.material.R.id.design_bottom_sheet)

    private val scrim: View?
        get() = findViewById(com.google.android.material.R.id.touch_outside)

    init {
        behavior.skipCollapsed = true
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (!isTracking || sheetAnimator?.isRunning == true)
                    return
                val parent = bottomSheet.parent as? View ?: return
                if (bottomSheet.height > 0)
                    setScrim((parent.height - bottomSheet.top).toFloat() / bottomSheet.height)
            }
        })
    }

    override fun onStart() {
        super.onStart()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        if (isDismissing)
            return
        val sheet = sheet ?: return
        sheet.visibility = View.INVISIBLE
        setScrim(0f)
        sheet.doOnLayout {
            sheet.translationY = sheet.height.toFloat()
            sheet.visibility = View.VISIBLE
            isTracking = true
            animateSheet(sheet, 0f, ENTER_DURATION, DecelerateInterpolator(2f))
        }
    }

    override fun dismiss() {
        if (isDismissing)
            return
        val sheet = sheet
        if (sheet == null || !sheet.isLaidOut || !isShowing || behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            super.dismiss()
            return
        }
        isDismissing = true
        animateSheet(sheet, sheet.height.toFloat(), EXIT_DURATION, AccelerateInterpolator(1.6f)) {
            super.dismiss()
            isDismissing = false
        }
    }

    private fun animateSheet(sheet: View, target: Float, duration: Long, interpolator: TimeInterpolator, onEnd: (() -> Unit)? = null) {
        sheetAnimator?.cancel()
        sheetAnimator = ValueAnimator.ofFloat(sheet.translationY, target).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener {
                val y = it.animatedValue as Float
                sheet.translationY = y
                if (sheet.height > 0)
                    setScrim(1f - y / sheet.height)
            }
            if (onEnd != null)
                doOnEnd { onEnd() }
            start()
        }
    }

    private fun setScrim(visible: Float) {
        val alpha = (SCRIM_ALPHA * visible.coerceIn(0f, 1f)).roundToInt()
        scrim?.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
    }

    companion object {
        private const val SCRIM_ALPHA = 128f
        private const val ENTER_DURATION = 300L
        private const val EXIT_DURATION = 220L
    }
}
