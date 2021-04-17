package com.mooner.starlight.plugincore

import kotlinx.serialization.Serializable

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
                "Double" -> value.toDouble()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}