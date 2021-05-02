package com.mooner.starlight.plugincore.language

data class MethodBlock(
    val blockName: String,
    val methodClass: Class<*>,
    val instance: Any,
    val isCustomClass: Boolean,
    val methods: List<Method>
)

data class Method(
    val methodName: String,
    val args: Array<Class<*>>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Method

        if (methodName != other.methodName) return false
        if (!args.contentEquals(other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = methodName.hashCode()
        result = 31 * result + args.contentHashCode()
        return result
    }
}