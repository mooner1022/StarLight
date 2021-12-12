package com.mooner.starlight.plugincore.config

class MutableConfigCategory(
    val data: MutableMap<String, TypedString>
): ConfigCategory {

    override operator fun contains(key: String): Boolean = data.containsKey(key)

    override operator fun get(key: String): Any? = data[key]?.cast()

    operator fun set(key: String, value: Any) {
        data[key] = TypedString.parse(value)
    }

    fun remove(key: String) {
        if (data.containsKey(key))
            data -= key
    }

    operator fun minusAssign(key: String) = remove(key)

    override fun getInt(key: String): Int? = data[key]?.castAs()

    override fun getLong(key: String): Long? = data[key]?.castAs()

    override fun getBoolean(key: String): Boolean? = data[key]?.castAs()

    override fun getString(key: String): String? = data[key]?.castAs()

    override fun getFloat(key: String): Float? = data[key]?.castAs()

    override fun getDouble(key: String): Double? = data[key]?.castAs()
}