package dev.mooner.starlight.plugincore.api

// Legacy support
typealias ApiFunction = ApiObject.Function
typealias ApiValue    = ApiObject.Value

sealed interface ApiObject {
    val name: String
    val returns: Class<*>

    data class Function(
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

    data class Value(
        override val name: String,
        override val returns: Class<*>
    ): ApiObject
}