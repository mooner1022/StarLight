/*
 * ScriptJson.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.languages

import dev.mooner.neonjs.NeonValue
import dev.mooner.starlight.media.script.ScriptValues
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * JSON in a script engine's own terms, for APIs that keep what scripts give them, such as `Store`.
 *
 * Java lists and maps aren't JS arrays and objects in either engine (`Array.isArray` is false, there is no `map`, and
 * NeonJS's `JSON.stringify` writes a list as an object), so an API that hands data back to a script asks the engine:
 * [parse] makes the engine's own objects and arrays, and [stringify] reads a value as the script's `JSON.stringify`
 * does. Both run on the thread the script called from.
 *
 * An engine gives its own to the API objects it creates, through [providing]. A language that doesn't, as a plugin's,
 * leaves them [Plain], which works with Java maps and lists.
 */
interface ScriptJson {

    /** The value [json] stands for, as the engine's own: a new object or array on each call. */
    fun parse(json: String): Any?

    /** [value] as JSON, written by the engine's `JSON.stringify`; null when it has no JSON form, as `undefined`. */
    fun stringify(value: Any?): String?

    companion object {

        private val provided = ThreadLocal<ScriptJson?>()

        /** Runs [block], the creation of a project's API objects, with [json] as what [current] gives. */
        fun <T> providing(json: ScriptJson, block: () -> T): T {
            val previous = provided.get()
            provided.set(json)
            return try {
                block()
            } finally {
                provided.set(previous)
            }
        }

        /** The JSON of the engine creating API objects on this thread; [Plain] when no engine gave one. */
        fun current(): ScriptJson =
            provided.get() ?: Plain
    }

    /** JSON with Java values: objects are maps, arrays are lists, numbers are `Long` or `Double`. */
    object Plain : ScriptJson {

        override fun parse(json: String): Any? =
            Json.parseToJsonElement(json).toValue()

        override fun stringify(value: Any?): String? {
            if (value is NeonValue && value.isUndefined || value is Unit)
                return null
            return value.toElement().toString()
        }

        private fun JsonElement.toValue(): Any? =
            when (this) {
                is JsonNull      -> null
                is JsonObject    -> entries.associateTo(linkedMapOf()) { (key, value) -> key to value.toValue() }
                is JsonArray     -> mapTo(arrayListOf()) { it.toValue() }
                is JsonPrimitive -> when {
                    isString -> content
                    else     -> booleanOrNull ?: longOrNull ?: doubleOrNull
                }
            }

        private fun Any?.toElement(): JsonElement =
            when (val value = ScriptValues.unwrap(this)) {
                null            -> JsonNull
                is Boolean      -> JsonPrimitive(value)
                is Double       -> if (value.isFinite()) JsonPrimitive(value) else JsonNull
                is Float        -> if (value.isFinite()) JsonPrimitive(value) else JsonNull
                is Number       -> JsonPrimitive(value)
                is CharSequence -> JsonPrimitive(value.toString())
                is Map<*, *>    -> JsonObject(value.entries.associate { (key, item) -> key.toString() to item.toElement() })
                is Collection<*> -> JsonArray(value.map { it.toElement() })
                is Array<*>     -> JsonArray(value.map { it.toElement() })
                else            -> throw IllegalArgumentException("${value.javaClass.name} has no JSON form")
            }
    }
}
