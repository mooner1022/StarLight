/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.utils

import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate

fun tr(english: String, korean: String): String =
    translate {
        Locale.ENGLISH { english }
        Locale.KOREAN  { korean }
    }
