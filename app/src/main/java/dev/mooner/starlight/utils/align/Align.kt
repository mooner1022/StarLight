package dev.mooner.starlight.utils.align

import androidx.annotation.DrawableRes

data class Align <T>(
    val name: String,
    val reversedName: String,
    @DrawableRes
    val icon: Int,
    val comparator: Comparator<in T>
)