package com.mooner.starlight.plugincore.theme

import com.mooner.starlight.plugincore.logger.Logger

class ThemeManager {
    companion object {
        private val T = ThemeManager::class.simpleName!!
        private val themes: HashMap<String, Theme> = hashMapOf()

        fun addTheme(theme: Theme) {
            if (!themes.containsKey(theme.id)) {
                themes[theme.id] = theme
                Logger.d(T, "Added theme ${theme.name} (${theme.id})")
            }
        }
    }
}