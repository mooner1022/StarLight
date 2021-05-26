package com.mooner.starlight.plugincore.theme

data class Theme(
    val name: String,
    val id: String,
    val dayColor: ThemeColor,
    val nightColor: ThemeColor
)

data class ThemeColor(
    val mainBright: Int,
    val mainDark: Int,
    val subBright: Int,
    val subDark: Int
)