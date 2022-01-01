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

    /**
     * Unique ID of the language.
     */
    abstract val id: String

    /**
     * The name being displayed to users.
     */
    abstract val name: String

    /**
     * Extension of file which contains code, without a dot in front.
     */
    abstract val fileExtension: String

    /**
     * true if the compiled scope requires release after execution, false if not.
     */
    abstract val requireRelease: Boolean

    /**
     * List of config objects injected in project config.
     */
    open val configObjectList: List<CategoryConfigObject> = listOf()

    /**
     * Default code used when a project is created.
     */
    abstract val defaultCode: String

    /**
     * Called when the value of config object defined above is saved to a local file.
     *
     * @param updated [Map] that contains id as key and updated value as value
     */
    open fun onConfigUpdated(updated: Map<String, Any>) {}

    /**
     * Called when the value of config object defined above is updated.
     *
     * @param id id of config object
     * @param view view which the config object is being drawn
     * @param data updated value
     */
    open fun onConfigChanged(id: String, view: View?, data: Any) {}

    /**
     * Compiles code with given code and apis
     *
     * @param code main code to compile
     * @param apis list of [Api]s to include
     * @param project the project that requested compilation
     *
     * @return scope of compilation result
     */
    abstract fun compile(code: String, apis: List<Api<Any>>, project: Project?): Any

    /**
     * Releases the compiled scope
     *
     * @param scope scope which is compiled and used
     */
    open fun release(scope: Any) {}

    /**
     * Calls a function defined in the scope with arguments provided
     *
     * @param scope scope which is compiled and used
     * @param functionName name of the function being called
     * @param args arguments passed to the function being called
     * @param onError callback called when an error occurs while running.
     */
    abstract fun callFunction(scope: Any, functionName: String, args: Array<out Any>, onError: (e: Exception) -> Unit = {})

    /**
     * Compiles and runs a code instantly
     *
     * @param code code to execute
     * @return [Any] value returned by the executed code
     */
    abstract fun eval(code: String): Any

    private var configFile: File? = null

    internal fun setConfigFile(path: File) {
        configFile = path
    }

    /**
     * Retrieves the config of language set by user.
     *
     * @return the deserialized object of config values, wrapped with [ConfigCategory]
     */
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