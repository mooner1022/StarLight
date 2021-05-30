package com.mooner.starlight.plugincore.theme

import androidx.annotation.ColorLong

data class Theme(
    val name: String,
    val id: String,
    val dayColor: ThemeColor,
    val nightColor: ThemeColor
)

data class ThemeColor(
    @ColorLong
    val mainBright: Long = ThemeManager.THEME_DEFAULT.dayColor.mainBright,
    @ColorLong
    val mainDark: Long = ThemeManager.THEME_DEFAULT.dayColor.mainDark,
    @ColorLong
    val subBright: Long = ThemeManager.THEME_DEFAULT.dayColor.subBright,
    @ColorLong
    val subDark: Long = ThemeManager.THEME_DEFAULT.dayColor.subDark,
    @ColorLong
    val toolbar: Long = ThemeManager.THEME_DEFAULT.dayColor.toolbar,
    @ColorLong
    val toolbarText: Long = ThemeManager.THEME_DEFAULT.dayColor.toolbarText,
    @ColorLong
    val background: Long = ThemeManager.THEME_DEFAULT.dayColor.background,
    @ColorLong
    val card: Long = ThemeManager.THEME_DEFAULT.dayColor.card,
    @ColorLong
    val cardText: Long = ThemeManager.THEME_DEFAULT.dayColor.cardText,
    @ColorLong
    val enabled: Long = ThemeManager.THEME_DEFAULT.dayColor.enabled,
    @ColorLong
    val enabledText: Long = ThemeManager.THEME_DEFAULT.dayColor.enabledText,
    @ColorLong
    val disabled: Long = ThemeManager.THEME_DEFAULT.dayColor.disabled,
    @ColorLong
    val disabledText: Long = ThemeManager.THEME_DEFAULT.dayColor.disabledText,
)