/*
 * FontUrils.kt created by Minki Moon(mooner1022) on 9/2/23, 1:48 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.utils

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat

private var typefaceCache: MutableMap<Int, Typeface> = hashMapOf()
internal fun getTypeface(context: Context, @FontRes res: Int): Typeface? =
    typefaceCache[res]
        ?: context
            .let { ResourcesCompat.getFont(it, res) }
            ?.also { typefaceCache[res] = it }