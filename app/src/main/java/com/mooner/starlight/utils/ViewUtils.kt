package com.mooner.starlight.utils

import android.widget.ImageView
import androidx.core.widget.NestedScrollView

class ViewUtils {
    companion object {

        fun NestedScrollView.bindFadeImage(imageView: ImageView) {
            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                if (scrollY in 0..200) {
                    imageView.alpha = 1f - (scrollY / 200.0f)
                } else {
                    imageView.alpha = 0f
                }
            }
        }
    }
}