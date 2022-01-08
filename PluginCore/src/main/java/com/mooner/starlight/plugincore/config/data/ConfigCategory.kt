package com.mooner.starlight.plugincore.config.data

interface ConfigCategory {
    operator fun contains(key: String): Boolean

    fun getInt(key: String): Int?

    fun getInt(key: String, default: Int): Int = getInt(key)?: default

    fun getLong(key: String): Long?

    fun getLong(key: String, default: Long): Long = getLong(key)?: default

    fun getBoolean(key: String): Boolean?

    fun getBoolean(key: String, default: Boolean): Boolean = getBoolean(key)?: default

    fun getString(key: String): String?

    fun getString(key: String, default: String): String = getString(key)?: default

    fun getFloat(key: String): Float?

    fun getFloat(key: String, default: Float): Float = getFloat(key)?: default

    fun getDouble(key: String): Double?

    fun getDouble(key: String, default: Double): Double = getDouble(key)?: default
}