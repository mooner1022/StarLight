/*
 * RuleData.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings.notifications

import dev.mooner.starlight.plugincore.chat.MessageParserSpec
import dev.mooner.starlight.plugincore.config.data.PrimitiveTypedString
import dev.mooner.starlight.plugincore.config.data.typedAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RuleData(
    @SerialName("package_name")
    val packageName: String,
    @SerialName("user_id")
    val userId: Int,
    @SerialName("parser_spec_id")
    val parserSpecId: String
) {

    fun convert(specs: Array<MessageParserSpec>): Map<String, PrimitiveTypedString>? {
        val specIdx = specs.indexOfFirst { it.id == parserSpecId }
        if (specIdx == -1)
            return null
        return mapOf(
            "package_name" to (packageName typedAs "String"),
            "user_id" to (userId.toString() typedAs "String"),
            "parser_spec_id" to (parserSpecId typedAs "String"),
            "parser_spec" to (specIdx.toString() typedAs "Int")
        )
    }

    companion object {

        fun from(map: Map<String, PrimitiveTypedString>): RuleData {
            return RuleData(
                packageName  = map["package_name"]!!.castAs(),
                userId       = map["user_id"]!!.castAs<String>().toInt(),
                parserSpecId = map["parser_spec_id"]!!.castAs()
            )
        }
    }
}