package com.mooner.starlight.plugincore.config

import com.mooner.starlight.plugincore.Session
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class TypedString(
    val type: String,
    val value: String
) {
    companion object {
        private val castable: Array<String> = arrayOf("String", "Boolean", "Float", "Int", "Long", "Double")

        fun parse(value: Any): TypedString {
            val className = value::class.simpleName
            require(className in castable) { "Un-castable type: $className" }
            return TypedString(
                type = className!!,
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
                //else -> Class.forName(type).cast(value)
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> castAs(): T = cast() as T

    fun encode(): String = Session.json.encodeToString(this)
}

infix fun String.typed(type: String): TypedString = TypedString(type, this)