/*
 * ParserSpecManager.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.chat

import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.utils.warnTranslated

object ParserSpecManager {

    private val logger = LoggerFactory.logger {  }

    private val specs: MutableMap<String, MessageParserSpec> = hashMapOf()

    fun registerSpec(spec: MessageParserSpec) {
        val id = spec.id
        if (id !in specs)
            specs[id] = spec
        else
            logger.warnTranslated {
                Locale.ENGLISH { "Rejecting MessageParserSpec registration of duplicated id: $id" }
                Locale.KOREAN  { "중복된 MessageParserSpec id의 등록을 거부했습니다: $id" }
            }
    }

    fun getAllSpecs(): Map<String, MessageParserSpec> =
        specs

    fun getSpecById(id: String): MessageParserSpec? {
        return if (id == "android_p")
            specs["android_r"] // Redirect old id
        else
            specs[id]
    }
}