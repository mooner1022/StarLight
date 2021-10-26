package com.mooner.starlight.plugincore.api

data class ApiFunction(
    val name: String,
    val args: Array<Class<*>> = arrayOf(),
    val returns: Class<*> = Unit::class.java
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApiFunction

        if (name != other.name) return false
        if (!args.contentEquals(other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + args.contentHashCode()
        return result
    }
}