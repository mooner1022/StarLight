package com.mooner.starlight.plugincore.language

import com.mooner.starlight.plugincore.config.ConfigCategory
import com.mooner.starlight.plugincore.config.ConfigCategoryImpl
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

    protected fun getLanguageConfig(): ConfigCategory {
        val data = if (configFile == null || !configFile!!.isFile || !configFile!!.exists()) mapOf() else {
            val raw = configFile!!.readText()
            val typed: Map<String, Map<String, TypedString>> =
                if (raw.isNotBlank())
                    json.decodeFromString(raw)
                else
                    emptyMap()
            typed[id]?: emptyMap()
        }
        return ConfigCategoryImpl(data)
    }

    protected fun getAsset(directory: String): File = File(Session.languageManager.getAssetPath(id), directory)

    protected fun getAssetOrNull(directory: String): File? = with(getAsset(directory)) {
        if (exists() && canRead()) this
        else null
    }

    fun getIconFile(): File = getAsset("icon.png")

    fun getIconFileOrNull(): File? = getAssetOrNull("icon.png")
}