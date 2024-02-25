/*
 * TypedString.kt created by Minki Moon(mooner1022) on 4/23/23, 11:26 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config.data

import dev.mooner.starlight.plugincore.Session
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
sealed interface TypedString <T> {

    val value: String

    fun cast(): T

    fun tryCast(): T?
}

@Serializable
data class PrimitiveTypedString(
    val type: String,
    override val value: String
): TypedString<Any> {

    override fun cast(): Any {
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

    override fun tryCast(): Any? {
        return try {
            cast()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> castAs(): T = cast() as T

    fun encode(): String = Session.json.encodeToString(this)

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

    companion object {
        private val castable: Array<String> = arrayOf("String", "Boolean", "Float", "Int", "Long", "Double")

        private fun isCastable(typeName: String?): Boolean =
            typeName in castable

        fun from(value: Any): PrimitiveTypedString {
            val className = value::class.simpleName
            require(isCastable(className)) { "Un-castable type: $className" }
            return PrimitiveTypedString(
                type = className!!,
                value = value.toString()
            )
        }
    }
}

infix fun String.typedAs(type: String): PrimitiveTypedString =
    PrimitiveTypedString(type, this)