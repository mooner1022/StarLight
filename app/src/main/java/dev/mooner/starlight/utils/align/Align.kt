package dev.mooner.starlight.utils.align

import androidx.annotation.DrawableRes

data class Align <T>(
    val name: String,
    val reversedName: String,
    @DrawableRes
    val icon: Int,
    val sort: (list: List<T>, args: Map<String, Boolean>) -> List<T>
)