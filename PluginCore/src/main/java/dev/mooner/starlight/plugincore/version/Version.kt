package dev.mooner.starlight.plugincore.version

import kotlinx.serialization.Serializable

@Serializable(with = VersionSerializer::class)
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val build: String? = null
) {
    companion object {

        @JvmStatic
        fun fromString(string: String): Version {
            if (string.count{ it == '.' } < 2) throw IllegalArgumentException("Illegal version string")
            try {
                val split = string.split(".")

                return Version(
                    major = split[0].toInt(),
                    minor = split[1].toInt(),
                    patch = split[2].toInt(),
                    build = if (split.size > 3) split[3] else null
                )
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to parse version string: $e")
            }
        }

        @JvmStatic
        fun check(string: String): Boolean {
            if (string.count{ it == '.' } < 2) return false
            try {
                val split = string.split(".")

                repeat(3) { index ->
                    if (split[index].toIntOrNull() == null) return false
                }
            } catch (e: Exception) {
                return false
            }
            return true
        }
    }

    override fun toString(): String =
        (if (build != null)
            arrayOf(major, minor, patch, build)
        else
            arrayOf(major, minor, patch)).joinToString(".")

    override fun equals(other: Any?): Boolean = when(other) {
        null -> false
        is String -> other == this.toString() || other == "$major.$minor"
        is Version -> other.major == this.major && other.minor == this.minor
        else -> false
    }

    fun isCompatibleWith(version: Version): Boolean =
        version.major == this.major

    infix fun compatibleWith(version: Version): Boolean =
        isCompatibleWith(version)

    infix fun incompatibleWith(version: Version): Boolean =
        !isCompatibleWith(version)

    infix fun newerThan(target: Version): Boolean =
        this.major > target.major && this.minor > target.minor && this.patch > target.patch

    override fun hashCode(): Int {
        var result = major.hashCode()
        result = 31 * result + minor.hashCode()
        return result
    }
}