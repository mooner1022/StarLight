/*
 * ColorUtils.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.utils

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import dev.mooner.configdsl.R

/** What small text needs against its background to be read comfortably. */
private const val MIN_TEXT_CONTRAST = 4.5

/**
 * [color] as text that can be read on the page.
 *
 * Colours for titles are mostly picked against the dark theme, where a pale gold or orange stands out. On the
 * light theme the same colour all but disappears into the page, so there it is given a darker tone of the
 * same hue, just dark enough to read. A colour that already reads well, and any colour on the dark theme, is
 * returned as it is.
 */
@ColorInt
internal fun Context.legibleOnPage(@ColorInt color: Int): Int {
    val isNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    if (isNight)
        return color

    val page = ColorUtils.setAlphaComponent(getColor(R.color.background), 255)
    val opaque = ColorUtils.setAlphaComponent(color, 255)
    if (ColorUtils.calculateContrast(opaque, page) >= MIN_TEXT_CONTRAST)
        return color

    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(opaque, hsl)
    while (hsl[2] > 0f) {
        hsl[2] = (hsl[2] - 0.01f).coerceAtLeast(0f)
        val darker = ColorUtils.HSLToColor(hsl)
        if (ColorUtils.calculateContrast(darker, page) >= MIN_TEXT_CONTRAST)
            return darker
    }
    return color
}
