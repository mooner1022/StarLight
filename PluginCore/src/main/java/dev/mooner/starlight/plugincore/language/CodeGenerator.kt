/*
 * CodeGenerator.kt created by Minki Moon(mooner1022) on 12/16/23, 4:22 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.language

import kotlin.reflect.KClass

interface CodeGenerator {

    fun generateFunction(name: String, arguments: Array<Argument<*>>, returns: KClass<*>?): GeneratedFunction

    fun generateComment(isMultiLine: Boolean, isDocument: Boolean, content: String): String

    data class GeneratedFunction(
        val imports: List<String>,
        val function: String,
    )

    data class Argument<T: Any>(
        val name: String,
        val type: KClass<T>,
    )
}