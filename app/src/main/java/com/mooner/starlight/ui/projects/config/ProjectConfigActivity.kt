package com.mooner.starlight.ui.projects.config

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.R
import com.mooner.starlight.databinding.ActivityProjectConfigBinding
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.config.*
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.plugincore.utils.Icon
import com.mooner.starlight.ui.config.ParentAdapter
import com.mooner.starlight.utils.ViewUtils.Companion.bindFadeImage
import java.io.File


class ProjectConfigActivity: AppCompatActivity() {

    private val commonConfigs: List<CategoryConfigObject> = config {
        category {
            id = "general"
            title = "일반"
            items = items {
                button {
                    id = "open_folder"
                    title = "폴더 열기"
                    type = ButtonConfigObject.Type.FLAT
                    onClickListener = {
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
    }

    private val changedData: MutableMap<String, MutableMap<String, Any>> = hashMapOf()
    private lateinit var savedData: MutableMap<String, MutableMap<String, TypedString>>
    private lateinit var binding: ActivityProjectConfigBinding
    private lateinit var project: Project
    private var recyclerAdapter: ParentAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fabProjectConfig = binding.fabProjectConfig
        val configRecyclerView = binding.configRecyclerView

        val projectName = intent.getStringExtra("projectName")!!
        project = Session.projectManager.getProject(projectName)?: throw IllegalStateException("Cannot find project $projectName")
        savedData = (project.config as FileConfig).data

        recyclerAdapter = ParentAdapter(binding.root.context) { parentId, id, view, data ->
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
        }.apply {
            data = (commonConfigs + project.getLanguage().configObjectList + getCautiousConfigs())
            saved = savedData
            notifyItemRangeInserted(0, data.size)
        }

        fabProjectConfig.setOnClickListener { view ->
            if (recyclerAdapter!!.isHavingError) {
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

        val layoutManager = LinearLayoutManager(applicationContext)
        configRecyclerView.layoutManager = layoutManager
        configRecyclerView.adapter = recyclerAdapter

        val textViewConfigProjectName: TextView = findViewById(R.id.textViewConfigProjectName)
        textViewConfigProjectName.text = projectName
    }

    @SuppressLint("CheckResult")
    private fun getCautiousConfigs(): List<CategoryConfigObject> = config {
        category {
            id = "cautious"
            title = "위험"
            textColor = color { "#FF865E" }
            items = items {
                button {
                    id = "interrupt_thread"
                    title = "프로젝트 스레드 강제 종료"
                    description = "${project.activeJobs()}개의 작업이 실행중이에요."
                    type = ButtonConfigObject.Type.FLAT
                    onClickListener = {
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
                    onClickListener = {
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

    private fun openFolderInExplorer(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = FileProvider.getUriForFile(context, "$packageName.provider", file)
        intent.setDataAndType(uri, "*/*")
        startActivity(Intent.createChooser(intent, "폴더 열기"))
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerAdapter?.destroy()
        recyclerAdapter = null
    }
}