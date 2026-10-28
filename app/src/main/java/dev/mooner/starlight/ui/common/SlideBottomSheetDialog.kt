package dev.mooner.starlight.ui.common

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.view.doOnLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dev.mooner.starlight.R

open class SlideBottomSheetDialog(context: Context) : BottomSheetDialog(context, R.style.ThemeOverlay_StarLight_SlideBottomSheet) {

    private var sheetAnimator: ValueAnimator? = null
    private var isDismissing = false

    private val sheet: View?
        get() = findViewById(com.google.android.material.R.id.design_bottom_sheet)

    init {
        behavior.skipCollapsed = true
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}

            override fun onSlide(bottomSheet: View, slideOffset: Float) =
                updateDim(bottomSheet)
        })
    }

    override fun onStart() {
        super.onStart()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        if (isDismissing)
            return
        val sheet = sheet ?: return
        sheet.visibility = View.INVISIBLE
        sheet.doOnLayout {
            sheet.translationY = sheet.height.toFloat()
            sheet.visibility = View.VISIBLE
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
                sheet.translationY = it.animatedValue as Float
                updateDim(sheet)
            }
            if (onEnd != null)
                doOnEnd { onEnd() }
            start()
        }
    }

    private fun updateDim(sheet: View) {
        val parent = sheet.parent as? View ?: return
        if (sheet.height == 0)
            return
        val visible = (parent.height - sheet.top - sheet.translationY) / sheet.height
        window?.setDimAmount(DIM_AMOUNT * visible.coerceIn(0f, 1f))
    }

    companion object {
        private const val DIM_AMOUNT = 0.5f
        private const val ENTER_DURATION = 300L
        private const val EXIT_DURATION = 220L
    }
}
