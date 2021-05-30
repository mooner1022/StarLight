package com.mooner.starlight.plugincore.theme

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.switchmaterial.SwitchMaterial
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.core.GeneralConfig
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.utils.Utils

class ThemeManager {

    init {
        addTheme(THEME_INTERSTELLAR)
        addTheme(THEME_ORANGE)
    }

    companion object {
        private val T = ThemeManager::class.simpleName!!
        private val themes: HashMap<String, Theme> = hashMapOf()

        const val COLOR_MAIN_BRIGHT = 0
        const val COLOR_MAIN_DARK = 1
        const val COLOR_SUB_BRIGHT = 2
        const val COLOR_SUB_DARK = 3
        const val COLOR_TOOLBAR = 4
        const val COLOR_TOOLBAR_TEXT = -4
        const val COLOR_BACKGROUND = 5
        const val COLOR_CARD = 6
        const val COLOR_CARD_TEXT = -6
        const val COLOR_ENABLED = 7
        const val COLOR_ENABLED_TEXT = -7
        const val COLOR_DISABLED = 8
        const val COLOR_DISABLED_TEXT = -8

        private val THEME_INTERSTELLAR = Theme(
            "Interstellar",
            "interstellar",
            ThemeColor(
                mainBright = 0xFFA2A9DA,
                mainDark = 0xFF2A2C39,
                subBright = 0xFF8E95BF,
                subDark = 0xFF191A25,
                toolbar = 0xFF2A2C39,
                toolbarText = 0xFFFFFFFF,
                background = 0xFFEAE8E8,
                card = 0xFFFFFFFF,
                cardText = 0xFF323232,
                enabled = 0xFF2A2C39,
                enabledText = 0xFFFFFFFF,
                disabled = 0xFFC1C1C1,
                disabledText = 0xFF323232
            ),
            ThemeColor(
                mainBright = 0xFFA2A9DA,
                mainDark = 0xFF2A2C39,
                subBright = 0xFF8E95BF,
                subDark = 0xFF191A25,
                toolbar = 0xFF2A2C39,
                toolbarText = 0xFFFFFFFF,
                background = 0xFF505050,
                card = 0xFF424242,
                cardText = 0xFFFFFFFF,
                enabled = 0xFF2A2C39,
                enabledText = 0xFFFFFFFF,
                disabled = 0xFFC1C1C1,
                disabledText = 0xFF323232
            )
        )

        private val THEME_ORANGE = Theme(
            "오렌지",
            "orange",
            ThemeColor(
                mainBright = 0xFFA2A9DA,
                mainDark = 0xFF2A2C39,
                subBright = 0xFF8E95BF,
                subDark = 0xFF191A25,
                toolbar = 0xFF2A2C39,
                toolbarText = 0xFFFFFFFF,
                background = 0xFFEAE8E8,
                card = 0xFFFFFFFF,
                cardText = 0xFF323232,
                enabled = 0xFFFFA557,
                enabledText = 0xFFFFFFFF,
                disabled = 0xFFC1C1C1,
                disabledText = 0xFF323232
            ),
            ThemeColor(
                mainBright = 0xFFA2A9DA,
                mainDark = 0xFF2A2C39,
                subBright = 0xFF8E95BF,
                subDark = 0xFF191A25,
                toolbar = 0xFF2A2C39,
                toolbarText = 0xFFFFFFFF,
                background = 0xFF505050,
                card = 0xFF424242,
                cardText = 0xFFFFFFFF,
                enabled = 0xFFFFA557,
                enabledText = 0xFFFFFFFF,
                disabled = 0xFFC1C1C1,
                disabledText = 0xFF323232
            )
        )

        val THEME_DEFAULT: Theme = THEME_ORANGE

        private var currentTheme: Theme = getTheme { it.id == Session.getGeneralConfig()[GeneralConfig.CONFIG_THEME_CURRENT] }?: THEME_DEFAULT

        fun addTheme(theme: Theme) {
            if (!themes.containsKey(theme.id)) {
                themes[theme.id] = theme
                Logger.d(T, "Added theme ${theme.name} (${theme.id})")
            }
        }

        fun setTheme(id: String) {
            if (themes.containsKey(id)) {

            }
        }

        fun getTheme(block: (Theme) -> Boolean): Theme? {
            return themes.values.find(block)
        }

        fun getAllThemes(): List<Theme> {
            return themes.values.toList()
        }

        fun getCurrentTheme(context: Context): ThemeColor {
            return if (Utils.isNightMode(context)) {
                currentTheme.nightColor
            } else {
                currentTheme.dayColor
            }
        }

        fun matchBackgroundColor(context: Context, map: Map<Int, Array<View>>) {
            for (item in map) {
                @ColorInt val color = 0xFFFFFFFF.toInt() and getColor(context, item.key)
                for (view in item.value) {
                    when(view) {
                        is CardView -> view.setCardBackgroundColor(color)
                        is CollapsingToolbarLayout -> {
                            view.setContentScrimColor(color)
                            view.setBackgroundColor(color)
                        }
                        else -> view.setBackgroundColor(color)
                    }
                }
            }
        }

        fun matchTextColor(context: Context, map: Map<Int, Array<View>>) {
            for (item in map) {
                @ColorInt val color = 0xFFFFFFFF.toInt() and getColor(context, item.key)
                for (view in item.value) {
                    when(view) {
                        is TextView -> {
                            view.setTextColor(color)
                        }
                        is EditText -> view.setTextColor(color)
                        else -> println("not a textview")
                    }
                }
            }
        }

        fun matchSwitchColor(context: Context, array: Array<View>) {
            @ColorInt val colorEnabled = 0xFFFFFFFF.toInt() and getColor(context, COLOR_ENABLED)
            @ColorInt val colorDisabled = 0xFFFFFFFF.toInt() and getColor(context, COLOR_DISABLED)
            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            )
            val colors = intArrayOf(
                colorEnabled,
                colorDisabled
            )
            for (view in array) {
                when(view) {
                    is SwitchCompat -> view.thumbTintList = ColorStateList(states, colors)
                    is SwitchMaterial -> view.thumbTintList = ColorStateList(states, colors)
                    is Switch -> view.thumbTintList = ColorStateList(states, colors)
                    else -> {
                        println("is not a switch")
                    }
                }
            }
        }

        @ColorInt
        fun getColor(context: Context, type: Int): Int {
            val theme = getCurrentTheme(context)
            return when(type) {
                COLOR_MAIN_BRIGHT -> theme.mainBright
                COLOR_MAIN_DARK -> theme.mainDark
                COLOR_SUB_BRIGHT -> theme.subBright
                COLOR_SUB_DARK -> theme.subDark
                COLOR_TOOLBAR -> theme.toolbar
                COLOR_TOOLBAR_TEXT -> theme.toolbarText
                COLOR_BACKGROUND -> theme.background
                COLOR_CARD -> theme.card
                COLOR_CARD_TEXT -> theme.cardText
                COLOR_ENABLED -> theme.enabled
                COLOR_ENABLED_TEXT -> theme.enabledText
                COLOR_DISABLED -> theme.disabled
                COLOR_DISABLED_TEXT -> theme.disabledText
                else -> 0xFFFFFFFF
            }.toInt()
        }
    }
}