package com.mooner.starlight.plugincore.method

data class MethodClass(
    val className: String,
    val clazz: Class<*>,
    val instance: Any,
    val functions: List<MethodFunction>
)

data class MethodFunction(
    val name: String,
    val args: Array<Class<*>> = arrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MethodFunction

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