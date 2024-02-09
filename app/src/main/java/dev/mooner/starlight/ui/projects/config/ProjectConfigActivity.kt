/*
 * Created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects.config

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import coil.load
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityProjectConfigBinding
import dev.mooner.starlight.databinding.ConfigButtonFlatBinding
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.config.*
import dev.mooner.starlight.plugincore.config.data.*
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.utils.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ProjectConfigActivity: AppCompatActivity() {
    private val changedData: MutableMap<String, MutableMap<String, Any>> = hashMapOf()
    private lateinit var binding: ActivityProjectConfigBinding
    private lateinit var project: Project
    private var configAdapter: ConfigAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindLogNotifier()

        val fabProjectConfig = binding.fabProjectConfig

        val projectName = intent.getStringExtra("projectName")!!
        project = projectManager.getProject(projectName)
            ?: throw IllegalStateException("Unable to find project $projectName")

        configAdapter = ConfigAdapter.Builder(this) {
            bind(binding.configRecyclerView)
            onConfigChanged { parentId, id, view, data ->
                changedData
                    .putIfAbsent(parentId, hashMapOf(id to data))
                    ?.put(id, data)

                if (!fabProjectConfig.isShown)
                    fabProjectConfig.show()
                if (parentId == project.getLanguage().id)
                    project.getLanguage().onConfigChanged(id, view, data)
            }
            structure {
                getConfigs(project)
            }
            @Suppress("UNCHECKED_CAST")
            savedData((project.config.getData() as MutableDataMap).injectEventIds(project.info.allowedEventIds))
            lifecycleOwner(this@ProjectConfigActivity)
        }.build()

        fabProjectConfig.setOnClickListener { view ->
            if (configAdapter?.hasError == true) {
                Snackbar.make(view, "올바르지 않은 설정이 있습니다. 확인 후 다시 시도해주세요.", Snackbar.LENGTH_SHORT).show()
                fabProjectConfig.hide()
                return@setOnClickListener
            }

            project.config.edit {
                for ((catId, data) in changedData) {
                    if (catId == "events" && "allowed_events" in data) {
                        project.info.allowedEventIds.clear()
                        (data["allowed_events"] as String)
                            .let<_, List<Map<String, PrimitiveTypedString>>>(Session.json::decodeFromString)
                            .mapNotNull { map -> map["event_id"]?.castAs<String>() }
                            .forEach(project.info.allowedEventIds::add)
                        project.saveInfo()

                        data -= "allowed_events"
                    }
                    category(catId).apply {
                        data.forEach(::setAny)
                    }
                }
            }

            val langConfIds = project.getLanguage().configStructure.map { it.id }
            val filtered = changedData.filter { it.key in langConfIds }
            if (filtered.isNotEmpty())
                project.getLanguage().onConfigUpdated(filtered)
            Snackbar.make(view, "설정 저장 완료", Snackbar.LENGTH_SHORT).show()
            fabProjectConfig.hide()
        }

        binding.scroll.bindFadeImage(binding.imageViewLogo)

        binding.leave.setOnClickListener { finish() }

        val textViewConfigProjectName: TextView = findViewById(R.id.textViewConfigProjectName)
        textViewConfigProjectName.text = projectName
        fabProjectConfig.hide()
    }

    private fun MutableDataMap.injectEventIds(ids: Set<String>): MutableDataMap {
        ids.map { id -> mapOf("event_id" to (id typedAs "String")) }
            .let(Json::encodeToString)
            .let { str ->
                if ("events" in this)
                    this["events"]!!["allowed_events"] = str typedAs "String"
                else
                    this["events"] = mutableMapOf("allowed_events" to (str typedAs "String"))
            }
        return this
    }

    private fun Project.getCustomButtonIDs(): Set<String> {
        return try {
            config
                .category("beta_features")
                .getString("custom_buttons")
                ?.let<_, List<Map<String, PrimitiveTypedString>>>(Session.json::decodeFromString)
                ?.map { it["button_id"]!!.castAs<String>() }
                ?.toSet()
                ?: emptySet()
        } catch (e: Exception) {
            logger.warn(translate {
                Locale.ENGLISH { "Failed to parse custom buttons: $e" }
                Locale.KOREAN  { "커스텀 버튼 목록을 불러오지 못함: $e" }
            })
            emptySet()
        }
    }

    private fun getConfigs(project: Project): ConfigStructure {
        val configs = config {
            category {
                id = "general"
                title = "일반"
                textColor = getColor(R.color.main_bright)
                items {
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
                    button {
                        id = "open_folder"
                        title = "폴더 열기"
                        type = ButtonConfigObject.Type.FLAT
                        setOnClickListener { _ ->
                            openFolderInExplorer(this@ProjectConfigActivity, project.directory)
                        }
                        icon = Icon.FOLDER
                        //backgroundColor = Color.parseColor("#B8DFD8")
                        iconTintColor = color { "#93B5C6" }
                    }
                    toggle {
                        id = "shutdown_on_error"
                        title = "오류 발생시 비활성화"
                        defaultValue = true
                        icon = Icon.ERROR
                        iconTintColor = color { "#FF5C58" }
                    }
                }
            }
            category {
                id = "events"
                title = "이벤트"
                textColor = getColor(R.color.main_bright)
                items {
                    list {
                        id = "allowed_events"
                        title = "호출이 허용된 이벤트"
                        description = "이 프로젝트를 호출할 수 있는 이벤트 ID 들이에요"
                        icon = Icon.NOTIFICATIONS_ACTIVE
                        iconTintColor = color { "#FF5C58" }
                        structure {
                            string {
                                id = "event_id"
                                title = "이벤트 ID"
                                icon = null
                                require = { string -> if (string.isBlank()) "이벤트 ID를 입력하세요" else null }
                            }
                        }
                        onInflate { view ->
                            LayoutInflater
                                .from(view.context)
                                .inflate(R.layout.config_button_card, view as FrameLayout, true)
                        }
                        onDraw { view, data ->
                            val binding = ConfigButtonFlatBinding.bind(view.findViewById(R.id.layout_configButton))

                            binding.title.text = data["event_id"] as String
                            binding.description.visibility = View.GONE
                            binding.icon.visibility = View.GONE
                        }
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
                    title = "실험적 기능"
                    textColor = getColor(R.color.main_bright)
                    items {
                        if (changeThreadPoolSize) {
                            seekbar {
                                id = "thread_pool_size"
                                title = "Thread pool 크기"
                                min = 1
                                max = 10
                                defaultValue = 3
                                icon = Icon.COMPRESS
                                iconTintColor = color { "#57837B" }
                            }
                        }
                        if (addCustomButtons) {
                            list {
                                id = "custom_buttons"
                                title = "사용자 지정 버튼"
                                icon = Icon.SETTINGS
                                iconTintColor = color { "#57837B" }
                                structure {
                                    string {
                                        id = "button_id"
                                        title = "id"
                                        description = "버튼 이벤트 호출 시 사용될 ID"
                                        icon = null
                                        require = { string ->
                                            if (string.isBlank())
                                                "버튼 id를 입력하세요"
                                            else if (string in project.getCustomButtonIDs())
                                                "이미 존재하는 버튼 id"
                                            else null
                                        }
                                    }
                                    spinner {
                                        id = "button_icon"
                                        title = "아이콘"
                                        icon = null
                                        defaultIndex = 0
                                        items = Icon.entries.map(Icon::name)
                                    }
                                }
                                onInflate { view ->
                                    LayoutInflater.from(view.context).inflate(R.layout.config_button_card, view as FrameLayout, true)
                                }
                                onDraw { view, data ->
                                    val binding = ConfigButtonFlatBinding.bind(view.findViewById(R.id.layout_configButton))

                                    binding.title.text = data["button_id"] as String
                                    binding.description.visibility = View.GONE

                                    val icon = if ("button_icon" in data)
                                        Icon.entries[data["button_icon"] as Int]
                                    else
                                        Icon.NONE
                                    binding.icon.load(icon.drawableRes)
                                }
                            }
                        }
                    }
                }
            }
            category {
                id = "cautious"
                title = "위험"
                textColor = color { "#FF865E" }
                items {
                    button {
                        id = "reload_info"
                        title = "프로젝트 정보 다시 로드"
                        description = "프로젝트 정보를 파일에서 다시 불러옵니다. 이 옵션은 앱의 정상적인 작동을 방해할 수 있습니다."
                        setOnClickListener { view ->
                            project.loadInfo()
                            Snackbar.make(
                                view,
                                "프로젝트 정보를 다시 불러왔어요.",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                        icon = Icon.REFRESH
                        iconTintColor = color { "#FF5C58" }
                    }
                    button {
                        id = "interrupt_thread"
                        title = "프로젝트 스레드 강제 종료"
                        description = "${project.activeJobs()}개의 작업이 실행중이에요."
                        setOnClickListener { view ->
                            val active = project.activeJobs()
                            project.stopAllJobs()
                            Snackbar.make(
                                view,
                                "${active}개의 작업을 강제 종료하고 할당 해제했어요.",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                        icon = Icon.LAYERS_CLEAR
                        //backgroundColor = Color.parseColor("#B8DFD8")
                        iconTintColor = color { "#FF5C58" }
                    }
                    button {
                        id = "destroy_scope"
                        title = "프로젝트 스코프 폐기"
                        description = "프로젝트의 컴파일된 스코프를 폐기합니다. 이후 프로젝트를 사용하기 위해선 다시 컴파일 해야 합니다."
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
                        title = "프로젝트 제거"
                        setOnClickListener { _ ->
                            MaterialDialog(
                                binding.root.context,
                                BottomSheet(LayoutMode.WRAP_CONTENT)
                            ).noAutoDismiss().show {
                                setCommonAttrs()
                                cancelOnTouchOutside(true)
                                //icon(res = R.drawable.ic_round_delete_forever_24)
                                title(text = "프로젝트를 정말로 제거할까요?")
                                message(text = "주의: 프로젝트 제거시 복구가 불가합니다.")
                                positiveButton(text = context.getString(R.string.delete)) {
                                    projectManager.removeProject(
                                        project,
                                        removeFiles = true
                                    )
                                    Snackbar.make(
                                        binding.root,
                                        "프로젝트를 제거했어요.",
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                    dismiss()
                                    finish()
                                }
                                negativeButton(text = context.getString(R.string.close)) {
                                    dismiss()
                                }
                            }
                        }
                        icon = Icon.DELETE_SWEEP
                        //backgroundColor = Color.parseColor("#B8DFD8")
                        iconTintColor = color { "#FF5C58" }
                    }
                }
            }
        }
        return configs
    }

    private fun showProjectRenameDialog() {
        MaterialDialog(
            this,
            BottomSheet(LayoutMode.WRAP_CONTENT)
        ).noAutoDismiss().show {
            var name: String? = null
            var updateMainScript = true

            setCommonAttrs()
            cancelOnTouchOutside(true)
            //icon(res = R.drawable.ic_round_delete_forever_24)
            title(text = translate { 
                Locale.ENGLISH { "Rename project" }
                Locale.KOREAN  { "프로젝트 이름 변경" }
            })
            message(text = translate {
                Locale.ENGLISH { "What name should it be changed to?" }
                Locale.KOREAN  { "어떤 이름으로 변경할까요?" }
            })
            configStruct(this@ProjectConfigActivity) {
                struct {
                    category {
                        id = "def"
                        items {
                            string {
                                id = "name"
                                title = "이름"
                                icon = Icon.EDIT
                                hint = translate {
                                    Locale.ENGLISH { "Enter name..." }
                                    Locale.KOREAN  { "변경될 이름 입력..." }
                                }
                                require = lambda@{ v ->
                                    if (v.isBlank()) {
                                        name = null
                                        return@lambda translate {
                                            Locale.ENGLISH { "Illegal name format" }
                                            Locale.KOREAN  { "올바르지 않은 이름 형식이에요" }
                                        }
                                    }
                                    if (projectManager.getProject(v, ignoreCase = true) != null) {
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
                                title = "메인 스크립트 이름 변경"
                                description = "메인 스크립트의 이름을 같이 변경합니다."
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
                    mapOf(
                        "def" to mapOf(
                            "name" to (project.info.name typedAs "String")
                        )
                    )
                }
            }
            positiveButton(text = context.getString(R.string.ok)) {
                if (name == null)
                    return@positiveButton
                project.rename(name!!, !updateMainScript)
                Snackbar.make(
                    binding.root,
                    translate {
                        Locale.ENGLISH { "Successfully renamed project." }
                        Locale.KOREAN  { "프로젝트의 이름을 성공적으로 변경했어요." }
                    },
                    Snackbar.LENGTH_SHORT
                ).show()
                dismiss()
                finish()
            }
            negativeButton(text = context.getString(R.string.cancel)) {
                dismiss()
            }
        }
    }

    private fun openFolderInExplorer(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = FileProvider.getUriForFile(context, "$packageName.provider", file)
        intent.setDataAndType(uri, "*/*")
        startActivity(Intent.createChooser(intent, "폴더 열기"))
    }
}