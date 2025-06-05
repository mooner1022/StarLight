package dev.mooner.starlight.plugincore

import dev.mooner.starlight.plugincore.version.Version

class Info {
    companion object {
        const val DEBUG = true

        @JvmStatic
        val PLUGINCORE_VERSION: Version =
            Version.fromString("0.3.2")
    }
}