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
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityProjectConfigBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.globalConfig
import dev.mooner.starlight.plugincore.config.ButtonConfigObject
import dev.mooner.starlight.plugincore.config.CategoryConfigObject
import dev.mooner.starlight.plugincore.config.TypedString
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.config.data.FileConfig
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.utils.bindFadeImage
import java.io.File

class ProjectConfigActivity: AppCompatActivity() {
    private val changedData: MutableMap<String, MutableMap<String, Any>> = hashMapOf()
    private lateinit var savedData: MutableMap<String, MutableMap<String, TypedString>>
    private lateinit var binding: ActivityProjectConfigBinding
    private lateinit var project: Project
    private var configAdapter: ConfigAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fabProjectConfig = binding.fabProjectConfig

        val projectName = intent.getStringExtra("projectName")!!
        project = Session.projectManager.getProject(projectName)?: throw IllegalStateException("Cannot find project $projectName")
        savedData = (project.config as FileConfig).data

        configAdapter = ConfigAdapter.Builder(this) {
            bind(binding.configRecyclerView)
            onConfigChanged { parentId, id, view, data ->
                if (changedData.containsKey(parentId)) {
                    changedData[parentId]!![id] = data
                } else {
                    changedData[parentId] = hashMapOf(id to data)
                }

                if (savedData.containsKey(parentId)) {
                    savedData[parentId]!![id] = TypedString.parse(data)
                } else {
                    savedData[parentId] = hashMapOf(id to TypedString.parse(data))
                }

                if (!fabProjectConfig.isShown) {
                    fabProjectConfig.show()
                }
                project.getLanguage().onConfigChanged(id, view, data)
            }
            configs {
                getConfigs(project)
            }
            savedData(savedData)
            lifecycleOwner(this@ProjectConfigActivity)
        }.build()

        fabProjectConfig.setOnClickListener { view ->
            if (configAdapter?.hasError == true) {
                Snackbar.make(view, "올바르지 않은 설정이 있습니다. 확인 후 다시 시도해주세요.", Snackbar.LENGTH_SHORT).show()
                fabProjectConfig.hide()
                return@setOnClickListener
            }

            (project.config as FileConfig).edit {
                for ((catId, data) in savedData) {
                    val category = getCategory(catId)
                    for ((key, value) in data) {
                        category.setAny(key, value.cast()?: error("Unable to cast $key"))
                    }
                }
            }

            val langConfIds = project.getLanguage().configObjectList.map { it.id }
            val filtered = changedData.filter { it.key in langConfIds }
            if (filtered.isNotEmpty()) project.getLanguage().onConfigUpdated(filtered)
            Snackbar.make(view, "설정 저장 완료", Snackbar.LENGTH_SHORT).show()
            fabProjectConfig.hide()
        }

        binding.scroll.bindFadeImage(binding.imageViewLogo)

        binding.leave.setOnClickListener { finish() }

        val textViewConfigProjectName: TextView = findViewById(R.id.textViewConfigProjectName)
        textViewConfigProjectName.text = projectName
    }

    private fun getConfigs(project: Project): List<CategoryConfigObject> {
        val configs = config {
            category {
                id = "general"
                title = "일반"
                textColor = color { "#706EB9" }
                items {
                    button {
                        id = "open_folder"
                        title = "폴더 열기"
                        type = ButtonConfigObject.Type.FLAT
                        setOnClickListener {
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
                    toggle {
                        id = "load_ext_scripts"
                        title = "외부 스크립트 로드"
                        description = "컴파일 시 /libs 폴더 내의 스크립트를 로드합니다."
                        defaultValue = false
                        icon = Icon.FOLDER
                        iconTintColor = color { "#C7B198" }
                    }
                }
            }
        } +
        project.getLanguage().configObjectList +
        config {
            val betaFeatureCategory = globalConfig.category("beta_features")
            if (betaFeatureCategory.getBoolean("change_thread_pool_size", false)) {
                category {
                    id = "beta_features"
                    title = "실험적 기능"
                    textColor = color("#706EB9")
                    items {
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
                }
            }
            category {
                id = "cautious"
                title = "위험"
                textColor = color { "#FF865E" }
                items {
                    button {
                        id = "interrupt_thread"
                        title = "프로젝트 스레드 강제 종료"
                        description = "${project.activeJobs()}개의 작업이 실행중이에요."
                        type = ButtonConfigObject.Type.FLAT
                        setOnClickListener {
                            val active = project.activeJobs()
                            project.stopAllJobs()
                            Snackbar.make(it, "${active}개의 작업을 강제 종료하고 할당 해제했어요.", Snackbar.LENGTH_SHORT).show()
                        }
                        icon = Icon.LAYERS_CLEAR
                        //backgroundColor = Color.parseColor("#B8DFD8")
                        iconTintColor = color { "#FF5C58" }
                    }
                    button {
                        id = "delete_project"
                        title = "프로젝트 제거"
                        type = ButtonConfigObject.Type.FLAT
                        setOnClickListener {
                            MaterialDialog(binding.root.context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                                cornerRadius(25f)
                                cancelOnTouchOutside(true)
                                noAutoDismiss()
                                //icon(res = R.drawable.ic_round_delete_forever_24)
                                title(text = "프로젝트를 정말로 제거할까요?")
                                message(text = "주의: 프로젝트 제거시 복구가 불가합니다.")
                                positiveButton(text = context.getString(R.string.delete)) {
                                    Session.projectManager.removeProject(project, removeFiles = true)
                                    Snackbar.make(binding.root, "프로젝트를 제거했어요.", Snackbar.LENGTH_SHORT).show()
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

    private fun openFolderInExplorer(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = FileProvider.getUriForFile(context, "$packageName.provider", file)
        intent.setDataAndType(uri, "*/*")
        startActivity(Intent.createChooser(intent, "폴더 열기"))
    }
}