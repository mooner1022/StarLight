/*
 * DotenvParser.kt created by Minki Moon(mooner1022) on 3/5/24, 9:00 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.utils

typealias EnvMap = Map<String, String>

class DotenvParser {

    companion object {
        private val KEY_REGEX         = "[a-zA-Z_]+[a-zA-Z0-9_]*".toRegex()
        private val INTERPOLATE_REGEX = "\\$\\{([a-zA-Z_]+[a-zA-Z0-9_]*)\\}".toRegex()

        private const val TOKEN_COMMENT            = '#'
        private const val TOKEN_BLOCK_QUOTE_SINGLE = "\'\'\'"
        private const val TOKEN_BLOCK_QUOTE_DOUBLE = "\"\"\""

        @JvmStatic
        fun parseString(string: String): EnvMap {
            val lines = string.split('\n')
            var cursor = 0

            var state: ParseState = ParseState.NONE
            val buffer: MutableList<String> = arrayListOf()

            var key = ""
            val entries: MutableMap<String, EntryValue> = linkedMapOf()

            while (cursor < lines.size) {
                val line = lines[cursor]

                if (state != ParseState.NONE) {
                    when(state) {
                        ParseState.BLOCK_SINGLE -> {
                            if (!line.endsWith(TOKEN_BLOCK_QUOTE_SINGLE)) {
                                buffer += line
                                cursor++
                                continue
                            }
                            if (line.length != 3)
                                buffer += line.drop(3)
                            entries[key] = EntryValue(buffer.joinToString("\n"), false)
                            state = ParseState.NONE
                        }
                        ParseState.BLOCK_DOUBLE -> {
                            if (!line.endsWith(TOKEN_BLOCK_QUOTE_DOUBLE)) {
                                buffer += line
                                cursor++
                                continue
                            }
                            if (line.length != 3)
                                buffer += line.drop(3)
                            entries[key] = EntryValue(buffer.joinToString("\n"), true)
                            state = ParseState.NONE
                        }
                    }
                } else {
                    if (line.isBlank() || line.trimStart()[0] == TOKEN_COMMENT) {
                        cursor++
                        continue
                    }
                    key = line.split('=')[0]
                    val keyLength = key.length
                    key = key.trim()
                    if (!isKeyValid(key))
                        error("Invalid key: $key (#${cursor + 1})")

                    val value = line.drop(keyLength + 1).trim()

                    if (value.startsWith(TOKEN_BLOCK_QUOTE_SINGLE)) {
                        if (value.length == 3) {
                            state = ParseState.BLOCK_SINGLE
                            buffer.clear()
                        } else if (value.endsWith(TOKEN_BLOCK_QUOTE_SINGLE)) {
                            entries[key] = EntryValue(value.drop(3).dropLast(3), false)
                        } else {
                            state = ParseState.BLOCK_SINGLE
                            buffer.clear()
                            buffer += value.drop(3)
                        }
                    } else if (value.startsWith(TOKEN_BLOCK_QUOTE_DOUBLE)) {
                        if (value.length == 3) {
                            state = ParseState.BLOCK_DOUBLE
                            buffer.clear()
                        } else if (value.endsWith(TOKEN_BLOCK_QUOTE_DOUBLE)) {
                            entries[key] = EntryValue(value.drop(3).dropLast(3), false)
                        } else {
                            state = ParseState.BLOCK_DOUBLE
                            buffer.clear()
                            buffer += value.drop(3)
                        }
                    } else if (value.isSingleQuoted()) {
                        val content = value.substring(1, value.length - 1)
                        if ('\'' in content)
                            error("Illegal value format: $content (#${cursor + 1})")
                        entries[key] = EntryValue(content, false)
                    } else if (value.isDoubleQuoted()) {
                        val content = value.substring(1, value.length - 1)
                        if ('\"' in content)
                            error("Illegal value format: $content (#${cursor + 1})")
                        entries[key] = EntryValue(content, true)
                    } else {
                        val nValue =
                            if (TOKEN_COMMENT in value)
                                value.substringBefore(TOKEN_COMMENT)
                            else
                                value
                        entries[key] = EntryValue(nValue, true)
                    }
                }

                cursor++
            }

            val result: MutableMap<String, String> = linkedMapOf()
            for ((nKey, value) in entries) {
                if (value.canInterpolate) {
                    for (match in INTERPOLATE_REGEX.findAll(value.content)) {
                        val formatKey = match.groupValues[1]
                        if (formatKey !in entries)
                            error("Interpolation key '$formatKey' does not exist (key $nKey)")
                        val refValue = entries[formatKey]!!.content
                        value.content = value.content.replace(match.value, refValue)
                    }
                }
                result[nKey] = value.content
            }
            return result
        }

        private fun isKeyValid(key: String): Boolean =
            KEY_REGEX.matches(key)

        private fun String.isSingleQuoted(): Boolean =
            startsWith('\'') && endsWith('\'')

        private fun String.isDoubleQuoted(): Boolean =
            startsWith('"') && endsWith('"')

        private data class EntryValue(
            var content        : String,
            val canInterpolate : Boolean = false,
        )

        private enum class ParseState {
            NONE,
            BLOCK_DOUBLE,
            BLOCK_SINGLE,
        }
    }
}