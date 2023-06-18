/*
 * TranslationBuilder.kt created by Minki Moon(mooner1022) on 22. 12. 25. 오후 3:11
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.translation

import java.util.*

class TranslationBuilder {

    private val localeMap: LocaleMap = EnumMap(Locale::class.java)
    private var formatMap: FormatMap? = null

    operator fun Locale.invoke(message: () -> String) {
        localeMap[this] = message()
    }

    fun format(vararg pairs: Pair<String, String>) {
        this.formatMap = pairs.toMap()
    }

    fun build(): Pair<LocaleMap, FormatMap> =
        localeMap to (formatMap ?: emptyMap())
}