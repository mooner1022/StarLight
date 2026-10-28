package dev.mooner.starlight.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

private val NIGHT_ICON_ACCENT = Color.parseColor("#DFC199")

fun Context.isNightMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

@ColorInt
fun Context.nightIconTint(@ColorInt color: Int): Int {
    if (!isNightMode())
        return color
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    if (hsl[1] < 0.15f)
        return color
    hsl[1] *= 0.5f
    hsl[2] = hsl[2].coerceIn(0.6f, 0.72f)
    val softened = ColorUtils.blendARGB(ColorUtils.HSLToColor(hsl), NIGHT_ICON_ACCENT, 0.2f)
    return ColorUtils.setAlphaComponent(softened, Color.alpha(color))
}
