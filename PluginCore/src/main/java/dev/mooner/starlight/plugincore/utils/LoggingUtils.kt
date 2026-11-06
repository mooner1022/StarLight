/*
 * LoggingUtils.kt created by Minki Moon(mooner1022) on 1/28/23, 11:09 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.utils

import dev.mooner.starlight.plugincore.logger.TLogger
import dev.mooner.starlight.plugincore.translation.TranslationBuilderBlock
import dev.mooner.starlight.plugincore.translation.TranslationManager

fun TLogger.verboseTranslated(block: TranslationBuilderBlock) {
    verbose { TranslationManager.translate(block) }
}

fun TLogger.debugTranslated(block: TranslationBuilderBlock) {
    debug { TranslationManager.translate(block) }
}

fun TLogger.infoTranslated(block: TranslationBuilderBlock) {
    info { TranslationManager.translate(block) }
}

fun TLogger.warnTranslated(block: TranslationBuilderBlock) {
    warn { TranslationManager.translate(block) }
}

fun TLogger.errorTranslated(block: TranslationBuilderBlock) {
    error { TranslationManager.translate(block) }
}