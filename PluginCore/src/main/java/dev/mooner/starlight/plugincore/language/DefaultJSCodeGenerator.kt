/*
 * DefaultJSCodeGenerator.kt created by Minki Moon(mooner1022) on 12/18/23, 1:59 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.language

import kotlin.reflect.KClass

class DefaultJSCodeGenerator: CodeGenerator {

    override fun generateFunction(
        name     : String,
        arguments: Array<CodeGenerator.Argument<*>>,
        returns  : KClass<*>?
    ): CodeGenerator.GeneratedFunction {
        return CodeGenerator.GeneratedFunction(
            imports = emptyList(),
            buildString {
                append("function ").append(name)
                append("(").append(arguments.joinToString(", ") { it.name }).append(") {\n")
                append("    \n")
                append("}")
            }
        )
    }

    override fun generateComment(isMultiLine: Boolean, isDocument: Boolean, content: String): String {
        return if (!isMultiLine)
            "//${content}";
        else {
            buildString {
                append("/*")
                if (isDocument)
                    append("*")
                append("\n")
                for (line in content.split("\n")) {
                    append(" * ").append(line).append("\n")
                }
                append(" */")
            }
        }
    }
}