/*
 * Language.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.language

import androidx.annotation.CallSuper
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.config.data.category.ConfigCategory
import dev.mooner.starlight.plugincore.config.data.category.internal.ConfigCategoryImpl
import dev.mooner.starlight.plugincore.pipeline.Pipeline
import dev.mooner.starlight.plugincore.pipeline.stage.LanguageStage
import dev.mooner.starlight.plugincore.pipeline.stage.PipelineStage
import dev.mooner.starlight.plugincore.pipeline.stage.plumber
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.utils.TimeUtils
import dev.mooner.starlight.plugincore.utils.decodeLegacyData
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
     * List of config objects inserted in project config.
     * Only category which has same ID with language is inserted.
     */
    open val configStructure: ConfigStructure = emptyList()

    /**
     * List of default code config objects inserted in project creation dialog.
     */
    open val defaultCodeStructure: ConfigStructure = emptyList()

    /**
     * Default code used when a project is created.
     *
     * %HEADER% - Position for header content
     * %BODY% - Position for main code
     */
    open val defaultCode: String = """
            %HEADER%
            %BODY%
        """.trimIndent()

    open val codeGenerator: CodeGenerator = DefaultJSCodeGenerator()

    open fun formatDefaultCode(fileName: String, events: List<ProjectEvent>): String {
        // TODO: Change with user-updatable comment
        val defaultComment = codeGenerator.generateComment(
            isMultiLine = true,
            isDocument = false,
            content = "$fileName created with Project StarLight ✦\nCreated on ${TimeUtils.formatCurrentDate("dd/MM/yy hh:mm a")}"
        )

        val imports: MutableSet<String> = hashSetOf()

        val duplicated: MutableMap<String, Boolean> = hashMapOf()
        for (event in events) {
            if (event.functionName in duplicated)
                continue
            if (events.count { it.functionName == event.functionName } > 1)
                duplicated[event.functionName] = false
        }

        val body = buildString {
            for (event in events) {
                if (duplicated[event.functionName] == true)
                    continue

                val generated = codeGenerator.generateFunction(event.functionName, event.argTypes, null)
                imports += generated.imports
                append("\n")
                if (event.functionComment != null)
                    append(codeGenerator.generateComment(
                        isMultiLine = true,
                        isDocument = true,
                        content = event.functionComment!!
                    )).append("\n")
                if (event.functionName in duplicated) {
                    append(codeGenerator
                        .generateComment(
                            isMultiLine = false,
                            isDocument = false,
                            content = "같은 함수명을 가진 이벤트가 발견되어 하나만 추가되었습니다."
                        )
                    ).append("\n")
                    duplicated[event.functionName] = true
                }
                append(generated.function).append("\n")
            }
        }

        val header = defaultComment + imports.joinToString("\n")
            .let { if (it.isNotBlank()) "\n${it}" else it }

        return defaultCode
            .replace(PLACEHOLDER_HEADER, header)
            .replace(PLACEHOLDER_BODY, body)
    }

    /**
     * Called when the value of config object defined above is saved to a local file.
     *
     * @param updated [Map] that contains id as key and updated value as value
     */
    @CallSuper
    open fun onConfigUpdated(updated: Map<String, Any>) {
        // Invalidate cache for lazy loading
        configCache = null
    }

    /**
     * Called when the value of config object defined above is updated.
     *
     * @param id id of config object
     * @param view view which the config object is being drawn
     * @param data updated value
     */
    open fun onConfigChanged(id: String, data: Any) {}

    /**
     * Compiles code with given code and apis
     *
     * @param code main code to compile
     * @param apis list of [Api]s to include
     * @param project the project that requested compilation
     *
     * @return scope of compilation result
     */
    abstract fun compile(code: String, apis: List<Api<*>>, project: Project?, classLoader: ClassLoader?): Any

    /**
     * Releases the compiled scope.
     * Should be called after scope evaluation, only if language requires release.
     *
     * @param scope scope which is compiled and used
     */
    open fun release(scope: Any) {}

    /**
     * Destroys and releases the scope, and all resources it was holding.
     * Should be called on the last stage of scope lifecycle.
     *
     * @param scope scope which is compiled and used
     */
    open fun destroy(scope: Any) {}

    /**
     * Calls a function defined in the scope with arguments provided
     *
     * @param scope scope which is compiled and used
     * @param functionName name of the function being called
     * @param args arguments passed to the function being called
     * @return return value of the called function
     */
    abstract fun callFunction(scope: Any, functionName: String, args: Array<out Any>): Any?

    /**
     * Compiles and runs a code instantly
     *
     * @param code code to execute
     * @return [Any] value returned by the executed code
     */
    abstract fun eval(code: String): Any

    private var configFile: File? = null
    private var configCache: ConfigCategory? = null

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
        return configCache ?: loadLanguageConfig().also { configCache = it }
    }

    private fun loadLanguageConfig(): ConfigCategory {
        val data = if (configFile == null || !configFile!!.isFile || !configFile!!.exists()) emptyMap() else {
            val raw = configFile!!.readText()
            val data =
                if (raw.isNotBlank())
                    runCatching {
                        json.decodeLegacyData(raw)
                    }.getOrElse {
                        json.decodeFromString<MutableDataMap>(raw)
                    }
                else
                    emptyMap()
            data[id] ?: emptyMap()
        }
        return ConfigCategoryImpl(data)
    }

    protected fun getAsset(directory: String): File =
        File(Session.languageManager.getAssetPath(id), directory)

    protected fun getAssetOrNull(directory: String): File? =
        with(getAsset(directory)) {
            if (exists() && canRead()) this
            else null
        }

    fun getIconFile(): File =
        getAsset("icon.png")

    fun getIconFileOrNull(): File? =
        getAssetOrNull("icon.png")

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
            compile(code, Session.apiManager.getApis(), project, project.getClassLoader())
        }
    )

    companion object {

        const val PLACEHOLDER_HEADER = "%HEADER%"
        const val PLACEHOLDER_BODY   = "%BODY%"
    }
}