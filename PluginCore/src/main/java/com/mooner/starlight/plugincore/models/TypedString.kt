package com.mooner.starlight.plugincore.models

import com.mooner.starlight.plugincore.core.Session
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class TypedString(
    val type: String,
    val value: String
) {
    companion object {

        fun parse(value: Any): TypedString {
            return TypedString(
                type = value::class.simpleName!!,
                value = value.toString()
            )
        }
    }

    fun cast(): Any? {
        return try {
            when(type) {
                "String" -> value
                "Boolean" -> value.toBoolean()
                "Float" -> value.toFloat()
                "Int" -> value.toInt()
                "Long" -> value.toLong()
                "Double" -> value.toDouble()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> castAs(): T = cast() as T

    fun encode(): String = Session.json.encodeToString(this)
}

infix fun String.typed(type: String): TypedString = TypedString(type, this)