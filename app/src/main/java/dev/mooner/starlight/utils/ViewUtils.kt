package dev.mooner.starlight.utils

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.TypedValue
import android.os.Build
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.NestedScrollView
import androidx.core.view.isVisible
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ViewNightHeaderBinding
import coil.ImageLoader
import coil.imageLoader
import coil.load
import coil.request.Disposable
import coil.request.ImageRequest
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import kotlin.reflect.full.primaryConstructor

private val LOG = LoggerFactory.logger {  }

fun NestedScrollView.bindFadeImage(imageView: ImageView) {
    setOnScrollChangeListener { _, _, scrollY, _, _ ->
        if (scrollY in 0..200) {
            imageView.alpha = 1f - (scrollY / 200.0f)
        } else {
            imageView.alpha = 0f
        }
    }
}

/**
 * Gives a page the header the main screen has in each theme: the sunset [art] in light mode and the night sky
 * ([nightHeader]) in dark mode. Whichever shows fades out as [scroll] moves the content over it.
 */
fun Context.bindPageHeader(scroll: NestedScrollView, art: ImageView, nightHeader: ViewNightHeaderBinding) {
    val isNight = isNightMode()
    art.isVisible = !isNight
    nightHeader.root.isVisible = isNight
    nightHeader.nightStars.brightness = 0.75f

    val header: View = if (isNight) nightHeader.root else art
    scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
        header.alpha = (1f - scrollY / 200f).coerceIn(0f, 1f)
    })
}

fun dpToPx(context: Context, dp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
}

inline fun ImageView.loadWithTint(
    @DrawableRes drawableResId: Int,
    @ColorRes tintColor: Int?,
    imageLoader: ImageLoader = context.imageLoader,
    builder: ImageRequest.Builder.() -> Unit = {}
): Disposable {
    ImageViewCompat.setImageTintList(this, tintColor?.let { color -> ColorStateList.valueOf(context.getColor(color)) })
    return load(drawableResId, imageLoader, builder)
}

inline fun ImageView.loadWithTint(
    data: Any?,
    imageLoader: ImageLoader = context.imageLoader,
    @ColorRes
    tintColor: Int?,
    builder: ImageRequest.Builder.() -> Unit = {}
): Disposable {
    ImageViewCompat.setImageTintList(this, if (tintColor == null) null else ColorStateList.valueOf(context.getColor(tintColor)))
    return load(data, imageLoader, builder)
}

private fun ProgressBar.setProgressGraceful(to: Int) {
    max = width

    val start = progress
    val end = ((to.toFloat() / 100f) * width).toInt()
    LOG.verbose { "Animating progressbar to= $to, start= $start, end= $end" }
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

private val Number.toPx
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        Resources.getSystem().displayMetrics
    )

fun dp(value: Int): Int =
    value.toPx.toInt()
fun dp(value: Float): Float =
    value.toPx

inline fun View.applyLayoutParams(block: LayoutParams.() -> Unit) {
    applyLayoutParams<LayoutParams>(block)
}

@JvmName("applyLayoutParamsTyped")
inline fun <reified T : LayoutParams> View.applyLayoutParams(
    block: T.() -> Unit
) {
    val params = (layoutParams ?: T::class.primaryConstructor!!.call(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) as T
    block(params)
    layoutParams = params
}

fun ComponentActivity.applyEdgeToEdge(
    rootView: View? = null,
    typeMask: Int = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
    onApplyWindowInsets: ((view: View, insets: Insets) -> Unit)? = null
) {
    enableEdgeToEdge()

    val rootView: View = rootView ?: window.decorView.findViewById(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
        val insets = windowInsets.getInsets(typeMask)
        view.updatePadding(insets.left, insets.top, insets.right, insets.bottom)
        onApplyWindowInsets?.invoke(view, insets)
        WindowInsetsCompat.CONSUMED
    }
}

/**
 * Lays a page out the way the main screen is: behind transparent system bars whose icons follow the theme,
 * with [rootView] kept clear of them and the page's background reaching the edges of the screen.
 * Without this a page gets the theme's grey status bar with white icons, and a black navigation bar.
 */
fun ComponentActivity.fitPage(rootView: View) {
    enableEdgeToEdge()
    // Below Android 8.1 the navigation bar's buttons are always white, so its own scrim has to stay.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
        window.navigationBarColor = getColor(R.color.background)
    if (rootView.background == null)
        rootView.setBackgroundColor(getColor(R.color.background))
    fitSystemBars(rootView)
}

/**
 * Keeps [rootView] clear of the system bars, display cutout and keyboard when the window draws behind
 * them, which Android 15 and later force on apps that target them. Otherwise these insets don't reach
 * the content, so nothing changes.
 */
fun ComponentActivity.fitSystemBars(rootView: View) {
    val initial = Insets.of(rootView.paddingLeft, rootView.paddingTop, rootView.paddingRight, rootView.paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
        )
        view.updatePadding(
            initial.left + insets.left,
            initial.top + insets.top,
            initial.right + insets.right,
            initial.bottom + insets.bottom,
        )
        WindowInsetsCompat.CONSUMED
    }
}

fun getScreenSizeDp(context: Context): Pair<Float, Float> {
    val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    val dpHeight: Float = displayMetrics.heightPixels / displayMetrics.density
    val dpWidth: Float = displayMetrics.widthPixels / displayMetrics.density
    return dpWidth to dpHeight
}