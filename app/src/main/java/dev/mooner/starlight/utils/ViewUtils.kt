package dev.mooner.starlight.utils

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.NestedScrollView
import coil.ImageLoader
import coil.imageLoader
import coil.loadAny
import coil.request.Disposable
import coil.request.ImageRequest
import dev.mooner.starlight.plugincore.logger.Logger

fun NestedScrollView.bindFadeImage(imageView: ImageView) {
    setOnScrollChangeListener { _, _, scrollY, _, _ ->
        if (scrollY in 0..200) {
            imageView.alpha = 1f - (scrollY / 200.0f)
        } else {
            imageView.alpha = 0f
        }
    }
}

fun dpToPx(context: Context, dp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
}

inline fun ImageView.loadAnyWithTint(
    data: Any?,
    imageLoader: ImageLoader = context.imageLoader,
    @ColorRes
    tintColor: Int?,
    builder: ImageRequest.Builder.() -> Unit = {}
): Disposable {
    ImageViewCompat.setImageTintList(this, if (tintColor == null) null else ColorStateList.valueOf(ContextCompat.getColor(context, tintColor)))
    return loadAny(data, imageLoader, builder)
}

private fun ProgressBar.setProgressGraceful(to: Int) {
    max = width

    val start = progress
    val end = ((to.toFloat() / 100f) * width).toInt()
    Logger.v("Animating progressbar to= $to, start= $start, end= $end")
    val animator = ValueAnimator.ofInt(start, end).apply {
        interpolator = AccelerateDecelerateInterpolator()
        startDelay = 0
        duration = 200
        addUpdateListener { valueAnimator ->
            progress = valueAnimator.animatedValue as Int
        }
    }

    animator.start()
}

var ProgressBar.graceProgress: Int
    get() = this.progress / width
    set(value) {
        setProgressGraceful(value)
    }
