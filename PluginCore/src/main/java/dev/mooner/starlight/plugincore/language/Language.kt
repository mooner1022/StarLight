/*
 * Language.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.language

import android.view.View
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.config.ConfigStructure
import dev.mooner.starlight.plugincore.config.data.PrimitiveTypedString
import dev.mooner.starlight.plugincore.config.data.category.ConfigCategory
import dev.mooner.starlight.plugincore.config.data.category.internal.ConfigCategoryImpl
import dev.mooner.starlight.plugincore.pipeline.Pipeline
import dev.mooner.starlight.plugincore.pipeline.stage.LanguageStage
import dev.mooner.starlight.plugincore.pipeline.stage.PipelineStage
import dev.mooner.starlight.plugincore.project.Project
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
    open val configStructure: ConfigStructure = listOf()

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
    abstract fun compile(code: String, apis: List<Api<*>>, project: Project?): Any

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

    /*
    private val langConfig: MutableConfigCategory by lazy {
        val data = if (configFile == null || !configFile!!.isFile || !configFile!!.exists()) mapOf() else {
            val raw = configFile!!.readText()
            val typed: Map<String, Map<String, TypedString>> =
                if (raw.isNotBlank())
                    json.decodeFromString(raw)
                else
                    emptyMap()
            typed[id]?: emptyMap()
        }
        MutableConfigCategory(data)
    }
     */

    /**
     * Retrieves the config of language set by user.
     *
     * @return the deserialized object of config values, wrapped with [ConfigCategory]
     */
    protected fun getLanguageConfig(): ConfigCategory {
        val data = if (configFile == null || !configFile!!.isFile || !configFile!!.exists()) mapOf() else {
            val raw = configFile!!.readText()
            val typed: Map<String, Map<String, PrimitiveTypedString>> =
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

    open fun toPipeline(): Pipeline<Pair<Project, String>, Any> {
        return plumber {
            initial<Pair<Project, String>, Any>(id, name) { (project, code) ->
                compile(code, Session.apiManager.getApis(), project, project.getClassLoader())
            }
        }
    }

    @Deprecated("Deprecated, use toPipeline().")
    open fun toPipelineStage(): PipelineStage<Pair<Project, String>, Any> = LanguageStage(
        id = this.id,
        name = this.name,
        run = { project, code ->
            compile(code, Session.apiManager.getApis(), project)
        }
    )
}