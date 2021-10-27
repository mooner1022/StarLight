package com.mooner.starlight.plugincore.language

import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Session.json
import com.mooner.starlight.plugincore.models.TypedString
import kotlinx.serialization.decodeFromString
import java.io.File

abstract class Language: ILanguage {
    private var configFile: File? = null

    internal fun setConfigFile(path: File) {
        configFile = path
    }

    protected fun getLanguageConfig(): Map<String, Any> {
        return if (configFile == null || !configFile!!.isFile || !configFile!!.exists()) mapOf() else {
            val raw = configFile!!.readText()
            val typed: Map<String, Map<String, TypedString>> =
                if (raw.isNotBlank())
                    json.decodeFromString(raw)
                else
                    emptyMap()
            (typed[id]?: emptyMap()).mapValues { it.value.cast()!! }
        }
    }

    protected fun getAsset(directory: String): File = File(Session.languageManager.getAssetPath(id), directory)

    fun getIconFile(): File = getAsset("icon.png")
}