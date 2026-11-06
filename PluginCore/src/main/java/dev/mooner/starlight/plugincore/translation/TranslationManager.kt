/*
 * TranslationManager.kt created by Minki Moon(mooner1022) on 22. 12. 25. 오후 3:05
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.translation

import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal typealias LocaleMap = MutableMap<Locale, () -> String>
internal typealias FormatMap = Map<String, String>
typealias TranslationBuilderBlock = TranslationBuilder.() -> Unit

private val LOG = LoggerFactory.logger { }

fun translate(block: TranslationBuilderBlock): String =
    TranslationManager.translate(block)

object TranslationManager {

    private const val DEFAULT_VALUE = "<TRANSLATION FAILURE>"
    private val eventScope: CoroutineScope =
        CoroutineScope(Dispatchers.Default)

    private var locale: Locale = Locale.ENGLISH

    internal fun init(locale: Locale) {
        this.locale = locale
    }

    fun getLocale(): Locale =
        locale

    /**
     *
     */
    fun translate(block: TranslationBuilderBlock): String =
        translateTo(locale, block)

    fun translateTo(locale: Locale, block: TranslationBuilderBlock): String {
        val (localeMap, formatMap) = TranslationBuilder()
            .apply(block)
            .build()

        var buffer: String = (localeMap[locale] ?: localeMap[Locale.ENGLISH])?.invoke() ?: DEFAULT_VALUE
        for ((tag, value) in formatMap)
            buffer = buffer.replace("{${tag}}", value)

        return buffer
    }

    private fun onLocaleUpdate(event: Events.Locale.Update) {
        LOG.info {
            translateTo(event.locale) {
                Locale.ENGLISH { "Updating app locale from $locale to ${event.locale}" }
                Locale.KOREAN { "앱 언어를 $locale 에서 ${event.locale} 로 변경했어요." }
            }
        }
        this.locale = event.locale
    }

    init {
        eventScope.launch {
            EventHandler.on(this, ::onLocaleUpdate)
        }
    }
}
