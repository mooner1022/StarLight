/*
 * LegacyUtils.kt created by Minki Moon(mooner1022) on 6/4/24, 11:23 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
@Deprecated("Retained for legacy support")
data class PrimitiveTypedString(
    val type: String,
    val value: String
) {

    fun cast(): Any {
        return when(type) {
            "String" -> value
            "Boolean" -> value.toBoolean()
            "Float" -> value.toFloat()
            "Int" -> value.toInt()
            "Long" -> value.toLong()
            "Double" -> value.toDouble()
            //else -> Class.forName(type).cast(value)
            else -> error("Un-castable type: $type")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> castAs(): T = cast() as T

    override fun equals(other: Any?): Boolean {
        return other.hashCode() == hashCode()
    }

    override fun toString(): String {
        return "TypedString(${type}:${value})"
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}

fun PrimitiveTypedString.toJsonElement(json: Json = Json): JsonElement {
    return when(type) {
        "String"  -> {
            if (value.startsWith("[{")) {
                json.decodeFromString<List<Map<String, PrimitiveTypedString>>>(value).let { legacy ->
                    val nList: MutableList<JsonElement> = arrayListOf()
                    for (entry in legacy) {
                        val map = entry.mapValues { (_, value) -> value.toJsonElement() }
                        nList += JsonObject(map)
                    }
                    JsonArray(nList)
                }
            } else
                JsonPrimitive(value)
        }
        "Boolean" -> JsonPrimitive(castAs<Boolean>())
        "Float"   -> JsonPrimitive(castAs<Float>())
        "Int"     -> JsonPrimitive(castAs<Int>())
        "Long"    -> JsonPrimitive(castAs<Long>())
        "Double"  -> JsonPrimitive(castAs<Double>())
        else -> JsonPrimitive(value)
    }
}