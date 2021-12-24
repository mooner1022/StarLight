package com.mooner.starlight.plugincore

class Info {
    companion object {
        val PLUGINCORE_VERSION: Version = Version.fromString("0.0.4")
    }
}

data class Version(
    val major: String,
    val minor: String,
) {
    companion object {
        fun fromString(str: String): Version {
            if (!str.contains(".")) throw IllegalArgumentException("Illegal version string")
            val spl: List<String>
            try {
                spl = str.split(".")
                if (spl.size != 3) throw IllegalArgumentException("Illegal version string: Invalid length")
                /*
                for (s in spl) {
                    if (s.toIntOrNull() == null) throw IllegalArgumentException("Illegal version string: Non-numerical version")
                }
                */
            } catch (e: Exception) {
                e.printStackTrace()
                throw IllegalArgumentException("Illegal version string")
            }
            return Version(
                major = "${spl[0]}.${spl[1]}",
                minor = spl[2]
            )
        }
    }

    override fun toString(): String {
        return "$major.$minor"
    }

    override fun equals(other: Any?): Boolean {
        return when(other) {
            null -> false
            is String -> other == this.toString() || other == "$major.$minor"
            is Version -> other.major == this.major && other.minor == this.minor
            else -> false
        }
    }

    fun isCompatibleWith(version: Version): Boolean {
        return version.major == this.major
    }

    override fun hashCode(): Int {
        var result = major.hashCode()
        result = 31 * result + minor.hashCode()
        return result
    }
}