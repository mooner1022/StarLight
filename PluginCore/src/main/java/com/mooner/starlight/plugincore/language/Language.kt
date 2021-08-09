package com.mooner.starlight.plugincore.language

import com.mooner.starlight.plugincore.TypedString
import com.mooner.starlight.plugincore.core.Session.Companion.json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

abstract class Language: ILanguage {
    private var configPath: File? = null

    internal fun setConfigPath(path: File) {
        configPath = path
    }

    fun getLanguageConfig(): Map<String, Any> {
        return if (configPath == null || !configPath!!.isFile || !configPath!!.exists()) mapOf() else {
            val typed: Map<String, TypedString> = json.decodeFromString(configPath!!.readText())
            typed.mapValues { it.value.cast()!! }
        }
    }
}