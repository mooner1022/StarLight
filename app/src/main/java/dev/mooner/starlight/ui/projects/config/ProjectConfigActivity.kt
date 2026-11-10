/*
 * Created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.configdsl.adapters.ConfigAdapter
import dev.mooner.configdsl.config
import dev.mooner.configdsl.findOption
import dev.mooner.configdsl.options.*
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.configdsl.utils.PrimitiveTypedString
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.api.ApiLevels
import dev.mooner.starlight.databinding.ActivityProjectConfigBinding
import dev.mooner.starlight.databinding.CardSimpleListItemBinding
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.config.*
import dev.mooner.starlight.plugincore.config.data.*
import dev.mooner.starlight.plugincore.api.ApiLevel
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.ui.common.confirmLeavingUnsaved
import dev.mooner.starlight.ui.common.showSheet
import dev.mooner.starlight.ui.projects.events.EventCatalog
import dev.mooner.starlight.ui.projects.events.showEventPicker
import dev.mooner.starlight.utils.*
import dev.mooner.starlight.utils.fitSkyPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.properties.Delegates.notNull
import kotlin.time.Duration.Companion.seconds

class ProjectConfigActivity: AppCompatActivity() {

    private val changedData: MutableDataMap = hashMapOf()
    /** The allowed event entries chosen but not saved yet. */
    private var pendingEventIds: List<String>? = null
    private var structure: ConfigStructure? = null
    private lateinit var binding: ActivityProjectConfigBinding
    private lateinit var project: Project
    private var configAdapter: ConfigAdapter? = null

    private var projectButtonIDs: Set<String> by notNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitSkyPage(binding.root, binding.skyHeader, binding.scroll, binding.leave)

        bindLogNotifier()

        val fabProjectConfig = binding.fabProjectConfig

        val projectName = intent.getStringExtra("projectName")!!
        project = projectManager.getProjectByName(projectName)
            ?: throw IllegalStateException("Unable to find project $projectName")

        projectButtonIDs = project
            .getCustomButtons()
            .map { it.first }
            .toSet()

        configAdapter = ConfigAdapter.Builder(this) {
            lifecycleOwner(this@ProjectConfigActivity)
            bind(binding.configRecyclerView)
            onValueUpdated { parentId, id, data, jsonData ->
                changedData
                    .putIfAbsent(parentId, hashMapOf(id to jsonData))
                    ?.put(id, jsonData)

                withContext(Dispatchers.Main) {
                    if (!fabProjectConfig.isShown)
                        fabProjectConfig.show()
                }
                if (parentId == project.getLanguage().id)
                    project.getLanguage().onConfigChanged(id, data)
                // The levels decide which of the allowed events the project receives.
                if (parentId == API_CATEGORY)
                    withContext(Dispatchers.Main) { updateEventSummary() }
            }
            structure {
                getStruct(project).also { structure = it }
            }
            @Suppress("UNCHECKED_CAST")
            configData((project.config.getData() as MutableDataMap)
                .injectApiLevels(project.info.apiLevels))
        }.build()

        val save: () -> Boolean = save@{
            // The button stays: hidden, there would be no way to save once the setting is put right.
            if (configAdapter?.hasError == true) {
                peek(getString(R.string.config_invalid), PeekType.ERROR)
                return@save false
            }

            pendingEventIds?.let { ids ->
                project.info.allowedEventIds.clear()
                project.info.allowedEventIds += ids
                project.saveInfo()
                pendingEventIds = null
            }
            project.config.edit {
                for ((catId, data) in changedData) {
                    // project.json holds the levels, as it does the allowed events.
                    if (catId == API_CATEGORY && API_LEVELS in data) {
                        project.info.apiLevels = data[API_LEVELS]!!.jsonArray.mapTo(linkedSetOf()) { it.jsonPrimitive.content }
                        project.saveInfo()
                        data -= API_LEVELS
                    }
                    category(catId).apply {
                        data.forEach(::setRaw)
                    }
                }
            }

            val langConfIds = project.getLanguage().configStructure.map { it.id }
            val filtered = changedData.filter { it.key in langConfIds }
            if (filtered.isNotEmpty())
                project.getLanguage().onConfigUpdated(filtered)

            createSuccessPeek(getString(R.string.config_saved), PeekAlert.Position.Bottom).peek()
            if (ApiLevels.hasListenerNameClash(project.info.apiLevels, project.info.languageId))
                peek(getString(R.string.api_levels_clash_warning), PeekType.INFO)
            fabProjectConfig.hide()
            true
        }
        fabProjectConfig.setOnClickListener { save() }

        bindPageHeader(binding.scroll, binding.skyHeader)

        confirmLeavingUnsaved(binding.leave, hasChanges = { fabProjectConfig.isOrWillBeShown }, save = save)

        val textViewConfigProjectName: TextView = findViewById(R.id.textViewConfigProjectName)
        textViewConfigProjectName.text = projectName
        fabProjectConfig.hide()
    }

    override fun onDestroy() {
        super.onDestroy()
        configAdapter?.destroy()
        configAdapter = null
    }

    private fun MutableDataMap.injectApiLevels(levelIds: Set<String>?): MutableDataMap {
        val ids = levelIds ?: Session.apiLevelManager.getLevels().map(ApiLevel::id)
        getOrPut(API_CATEGORY) { mutableMapOf() }[API_LEVELS] = JsonArray(ids.map(::JsonPrimitive))
        return this
    }

    private fun currentEventIds(): List<String> =
        pendingEventIds ?: project.info.allowedEventIds.toList()

    /** The levels chosen on this screen, saved or not. */
    private fun currentLevelIds(): Set<String>? =
        changedData[API_CATEGORY]?.get(API_LEVELS)?.jsonArray?.mapTo(linkedSetOf()) { it.jsonPrimitive.content }
            ?: project.info.apiLevels

    private fun eventSummary(): String =
        EventCatalog.summarize(this, currentEventIds(), currentLevelIds())

    private fun updateEventSummary() {
        structure?.findOption(EVENTS_CATEGORY, ALLOWED_EVENTS)?.updateDescription(eventSummary())
    }

    private fun openEventPicker() =
        showEventPicker(currentEventIds(), currentLevelIds()) { ids ->
            if (ids == currentEventIds())
                return@showEventPicker
            pendingEventIds = ids
            updateEventSummary()
            binding.fabProjectConfig.show()
        }

    private fun Project.getCustomButtons(): List<Pair<String, Icon>> {
        return try {
            runCatching {
                config
                    .category("beta_features")
                    .getString("custom_buttons")
                    ?.let<_, List<Map<String, PrimitiveTypedString>>>(json::decodeFromString)
                    ?.map { it["button_id"]!!.castAs<String>() to Icon.entries.toTypedArray()[it["button_icon"]!!.castAs()] }
                    ?: listOf()
            }.getOrElse {
                config
                    .category("beta_features")
                    .getList("custom_buttons")
                    ?.map<_, ProjectButtonData>(json::decodeFromJsonElement)
                    ?.map { it.id to (it.icon?.let(Icon.entries::get) ?: Icon.LAYERS) }
                    ?: listOf()
            }
        } catch (e: Exception) {
            logger.warn(translate {
                Locale.ENGLISH { "Failed to parse custom buttons: $e" }
                Locale.KOREAN  { "커스텀 버튼 목록을 불러오지 못함: $e" }
            })
            listOf()
        }
    }

    private fun getStruct(project: Project): ConfigStructure {
        return config {
            category {
                id = "general"
                title = getString(R.string.project_config_general_category)
                textColor = getColor(R.color.main_bright)
                items {
                    info {
                        id = "folder_location"
                        title = getString(R.string.project_config_folder_location_title)
                        description = project.directory.path
                        copyable = true
                        icon = Icon.FOLDER
                        iconTintColor = color { "#93B5C6" }
                    }
                    button {
                        id = "rename_project"
                        title = translate {
                            Locale.ENGLISH { "Rename Project" }
                            Locale.KOREAN  { "프로젝트 이름 변경" }
                        }
                        icon = Icon.EDIT
                        //iconTintColor = color { "#94A684" }
                        setOnClickListener(::showProjectRenameDialog)
                    }
                    toggle {
                        id = "shutdown_on_error"
                        title = getString(R.string.project_config_shutdown_on_error_title)
                        defaultValue = true
                        icon = Icon.ERROR
                        iconTintColor = color { "#FF5C58" }
                    }
                }
            }
            category {
                id = API_CATEGORY
                title = getString(R.string.api_levels_title)
                textColor = getColor(R.color.main_bright)
                items {
                    multiChoice {
                        id = API_LEVELS
                        title = getString(R.string.api_levels_title)
                        description = getString(R.string.api_levels_description)
                        icon = Icon.LAYERS
                        iconTintColor = color { "#B8A4C9" }
                        for (level in Session.apiLevelManager.getLevels())
                            option(level.id, level.name)
                    }
                }
            }
            category {
                id = EVENTS_CATEGORY
                title = getString(R.string.project_config_events_category)
                textColor = getColor(R.color.main_bright)
                items {
                    button {
                        id = ALLOWED_EVENTS
                        title = getString(R.string.event_picker_title)
                        description = eventSummary()
                        icon = Icon.NOTIFICATIONS_ACTIVE
                        iconTintColor = color { "#FF5C58" }
                        setOnClickListener(::openEventPicker)
                    }
                }
            }
            project.getLanguage().let { language ->
                combine(language.configStructure) { category ->
                    category.id == language.id
                }
            }

            val (changeThreadPoolSize, addCustomButtons) = GlobalConfig.category("beta_features")
                .run { arrayOf(
                    getBoolean("change_thread_pool_size", false),
                    getBoolean("add_custom_buttons", true)
                ) }

            if (changeThreadPoolSize || addCustomButtons) {
                category {
                    id = "beta_features"
                    title = getString(R.string.project_config_beta_features_category)
                    textColor = getColor(R.color.main_bright)
                    items {
                        if (changeThreadPoolSize) {
                            seekbar {
                                id = "thread_pool_size"
                                title = getString(R.string.project_config_thread_pool_size_title)
                                description = getString(R.string.project_config_thread_pool_size_desc)
                                min = 1
                                max = 10
                                defaultValue = project.getLanguage().defaultConcurrency
                                icon = Icon.COMPRESS
                                iconTintColor = color { "#57837B" }
                            }
                        }
                        if (addCustomButtons) {
                            list {
                                id = "custom_buttons"
                                title = getString(R.string.project_config_custom_buttons_title)
                                icon = Icon.SETTINGS
                                iconTintColor = color { "#57837B" }
                                structure {
                                    string {
                                        id = "button_id"
                                        title = "id"
                                        description = getString(R.string.project_config_button_id_desc)
                                        icon = null
                                        require = { string ->
                                            if (string.isBlank())
                                                getString(R.string.project_config_button_id_blank)
                                            else if (string in projectButtonIDs)
                                                getString(R.string.project_config_button_id_exists)
                                            else null
                                        }
                                    }
                                    spinner {
                                        id = "button_icon"
                                        title = getString(R.string.project_config_button_icon_title)
                                        icon = null
                                        defaultIndex = 0
                                        items = Icon.entries.map(Icon::name)
                                    }
                                }
                                onInflate { view ->
                                    LayoutInflater.from(view.context).inflate(R.layout.card_simple_list_item, view as FrameLayout, true)
                                }
                                onDraw<ProjectButtonData> { view, data ->
                                    val binding = CardSimpleListItemBinding.bind(view.findViewById(R.id.layout_simple_list_item))

                                    binding.title.text = data.id
                                    binding.description.visibility = View.GONE

                                    val icon = if (data.icon != null)
                                        Icon.entries[data.icon]
                                    else
                                        Icon.LAYERS
                                    binding.icon.load(icon.drawableRes)
                                }
                            }
                        }
                    }
                }
            }
            category {
                id = "cautious"
                title = getString(R.string.project_config_cautious_category)
                textColor = color { "#FF865E" }
                items {
                    button {
                        id = "reload_info"
                        title = getString(R.string.project_config_reload_info_title)
                        description = getString(R.string.project_config_reload_info_desc)
                        style = ButtonStyle.DANGER
                        confirm(getString(R.string.project_config_reload_info_confirm))
                        setOnClickListener { _ ->
                            project.loadInfo()
                            peek(getString(R.string.project_config_reload_info_done), PeekType.SUCCESS)
                        }
                        icon = Icon.REFRESH
                        iconTintColor = color { "#FF5C58" }
                    }
                    button {
                        id = "interrupt_thread"
                        title = getString(R.string.project_config_interrupt_thread_title)
                        bindDescription(every = 1.seconds) {
                            getString(R.string.project_config_interrupt_thread_desc, project.activeJobs(), project.queuedEvents())
                        }
                        style = ButtonStyle.DANGER
                        confirm { getString(R.string.project_config_interrupt_thread_confirm, project.activeJobs()) }
                        setOnClickListener { _ ->
                            val active = project.activeJobs()
                            val queued = project.queuedEvents()
                            project.stopAllJobs()
                            peek(getString(R.string.project_config_interrupt_thread_done, active, queued), PeekType.SUCCESS)
                        }
                        icon = Icon.LAYERS_CLEAR
                        //backgroundColor = Color.parseColor("#B8DFD8")
                        iconTintColor = color { "#FF5C58" }
                    }
                    button {
                        id = "destroy_scope"
                        title = getString(R.string.project_config_destroy_scope_title)
                        description = getString(R.string.project_config_destroy_scope_desc)
                        style = ButtonStyle.DANGER
                        confirm(getString(R.string.project_config_destroy_scope_confirm))
                        setOnClickListener { _ ->
                            project.destroy(requestUpdate = isForeground())
                            finish()
                        }
                        icon = Icon.DEVELOPER_BOARD_OFF
                        //backgroundColor = Color.parseColor("#B8DFD8")
                        iconTintColor = color { "#FF5C58" }
                    }
                    button {
                        id = "delete_project"
                        title = getString(R.string.project_config_delete_project_title)
                        style = ButtonStyle.DANGER
                        setOnClickListener { _ ->
                            showSheet {
                                title = getString(R.string.project_config_delete_project_confirm_title)
                                message = getString(R.string.project_config_delete_project_confirm_message)
                                icon = R.drawable.ic_round_delete_forever_24
                                positive(R.string.delete) {
                                    projectManager.removeProject(
                                        project,
                                        removeFiles = true
                                    )
                                    peek(getString(R.string.project_config_delete_project_done), PeekType.SUCCESS)
                                    finish()
                                }
                                negative(R.string.close)
                            }
                        }
                        icon = Icon.DELETE_SWEEP
                        //backgroundColor = Color.parseColor("#B8DFD8")
                        iconTintColor = color { "#FF5C58" }
                    }
                }
            }
        }
    }

    private fun showProjectRenameDialog() {
        var name: String? = null
        var updateMainScript = true

        showSheet {
            title = translate {
                Locale.ENGLISH { "Rename project" }
                Locale.KOREAN  { "프로젝트 이름 변경" }
            }
            message = translate {
                Locale.ENGLISH { "What name should it be changed to?" }
                Locale.KOREAN  { "어떤 이름으로 변경할까요?" }
            }
            icon = R.drawable.ic_round_edit_24
            configStruct(this@ProjectConfigActivity) {
                struct {
                    category {
                        id = "def"
                        items {
                            string {
                                id = "name"
                                title = getString(R.string.project_config_rename_name_title)
                                icon = Icon.EDIT
                                hint = translate {
                                    Locale.ENGLISH { "Enter name..." }
                                    Locale.KOREAN  { "변경될 이름 입력..." }
                                }
                                require = lambda@{ v ->
                                    if (v.isBlank() || !"(^[-_\\dA-Za-zㄱ-ㅎㅏ-ㅣ가-힣]+$)".toRegex().matches(v)) {
                                        name = null
                                        return@lambda translate {
                                            Locale.ENGLISH { "Illegal name format" }
                                            Locale.KOREAN  { "올바르지 않은 이름 형식이에요" }
                                        }
                                    }
                                    if (projectManager.getProjectByName(v, ignoreCase = true) != null) {
                                        name = null
                                        return@lambda translate {
                                            Locale.ENGLISH { "Name shadowed" }
                                            Locale.KOREAN  { "이미 존재하는 이름이에요." }
                                        }
                                    }
                                    name = v
                                    return@lambda null
                                }
                            }
                            toggle {
                                id = "preserve_main_script"
                                title = getString(R.string.project_config_rename_main_script_title)
                                description = getString(R.string.project_config_rename_main_script_desc)
                                defaultValue = true
                                icon = Icon.FILE
                                setOnValueChangedListener { _, toggle ->
                                    updateMainScript = toggle
                                }
                            }
                        }
                    }
                }
                data {
                    mutableMapOf(
                        "def" to mutableMapOf(
                            "name" to JsonPrimitive(project.info.name)
                        )
                    )
                }
            }
            positive(R.string.ok, autoDismiss = false) { sheet ->
                if (name == null)
                    return@positive
                project.rename(name!!, !updateMainScript)
                peek(translate {
                    Locale.ENGLISH { "Successfully renamed project." }
                    Locale.KOREAN  { "프로젝트의 이름을 성공적으로 변경했어요." }
                }, PeekType.SUCCESS)
                sheet.dismiss()
                finish()
            }
            negative(R.string.cancel)
        }
    }
}

@Serializable
internal data class ProjectButtonData(
    @SerialName("button_id")
    val id: String,
    @SerialName("button_icon")
    val icon: Int? = null,
)

private const val API_CATEGORY = "api"
private const val EVENTS_CATEGORY = "events"
private const val ALLOWED_EVENTS = "allowed_events"
private const val API_LEVELS = "levels"
