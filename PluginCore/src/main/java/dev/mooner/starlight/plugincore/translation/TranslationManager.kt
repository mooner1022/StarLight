/*
 * TranslationManager.kt created by Minki Moon(mooner1022) on 22. 12. 25. 오후 3:05
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.translation

internal typealias LocaleMap = MutableMap<Locale, String>

fun translate(block: TranslationBuilder.() -> Unit): String =
    TranslationManager.translate(block)

object TranslationManager {

    private const val DEFAULT_VALUE = "<TRANSLATION FAILURE>"

    private lateinit var locale: Locale

    internal fun init(locale: Locale) {
        this.locale = locale
    }

    /**
     *
     */
    fun translate(block: TranslationBuilder.() -> Unit): String {
        val localeMap = TranslationBuilder()
            .apply(block)
            .build()

        return localeMap[locale] ?: localeMap[Locale.ENGLISH] ?: DEFAULT_VALUE
    }
}