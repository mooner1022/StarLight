/*
 * RoundedRectDrawable.kt created by Minki Moon(mooner1022) on 25. 1. 17. 오후 11:33
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.presets

import android.graphics.drawable.PaintDrawable

class RoundedRectDrawable(color: Int, radius: Float) : PaintDrawable(color) {
  init {
    setCornerRadius(radius)
  }
}