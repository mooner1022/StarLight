package com.mooner.starlight.plugincore.language

import android.view.View
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.Session.json
import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.config.CategoryConfigObject
import com.mooner.starlight.plugincore.config.ConfigCategory
import com.mooner.starlight.plugincore.config.ConfigCategoryImpl
import com.mooner.starlight.plugincore.config.TypedString
import com.mooner.starlight.plugincore.project.Project
import kotlinx.serialization.decodeFromString
import java.io.File

abstract class Language {

    abstract val id: String

    abstract val name: String

    abstract val fileExtension: String

    abstract val requireRelease: Boolean

    open val configObjectList: List<CategoryConfigObject> = listOf()

    abstract val defaultCode: String

    open fun onConfigUpdated(updated: Map<String, Any>) {}

    open fun onConfigChanged(id: String, view: View?, data: Any) {}

    abstract fun compile(code: String, apis: List<Api<Any>>, project: Project?): Any

    open fun release(engine: Any) {}

    abstract fun callFunction(engine: Any, functionName: String, args: Array<out Any>, onError: (e: Exception) -> Unit = {})

    abstract fun eval(code: String): Any

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