package dev.mooner.starlight.plugincore.api

data class ApiFunction(
    override val name: String,
    val args: Array<Class<*>>,
    override val returns: Class<*> = Unit::class.java
): ApiObject {
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