package com.mooner.starlight.ui.projects.config

import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
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
import com.mooner.starlight.plugincore.config.ButtonConfigObject
import com.mooner.starlight.plugincore.config.CategoryConfigObject
import com.mooner.starlight.plugincore.config.config
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.models.TypedString
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
                    name = "폴더 열기"
                    type = ButtonConfigObject.Type.FLAT
                    onClickListener = {
                        println("dir= ${project.directory.path}")
                        val result = openFolderInExplorer(project.directory)
                        println(result)
                    }
                    icon = Icon.FOLDER
                    //backgroundColor = Color.parseColor("#B8DFD8")
                    iconTintColor = color { "#93B5C6" }
                }
                toggle {
                    id = "shutdown_on_error"
                    name = "오류 발생시 비활성화"
                    defaultValue = true
                    icon = Icon.ERROR
                    iconTintColor = color { "#FF6B6B" }
                }
            }
        }
    }
    private val cautiousConfigs: List<CategoryConfigObject> = config {
        category {
            id = "cautious"
            title = "위험"
            textColor = color { "#FF865E" }
            items = items {
                button {
                    id = "interrupt_thread"
                    name = "프로젝트 스레드 강제 종료"
                    type = ButtonConfigObject.Type.FLAT
                    onClickListener = {
                        project.destroy()
                    }
                    icon = Icon.LAYERS_CLEAR
                    //backgroundColor = Color.parseColor("#B8DFD8")
                    iconTintColor = color { "#FF5C58" }
                }
                button {
                    id = "delete_project"
                    name = "프로젝트 제거"
                    type = ButtonConfigObject.Type.FLAT
                    onClickListener = {
                        MaterialDialog(it.context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                            cornerRadius(25f)
                            cancelOnTouchOutside(true)
                            noAutoDismiss()
                            //icon(res = R.drawable.ic_round_delete_forever_24)
                            title(text = "프로젝트를 정말로 제거할까요?")
                            message(text = "주의: 프로젝트 제거시 복구가 불가합니다.")
                            positiveButton(text = context.getString(R.string.delete)) {
                                Session.projectManager.removeProject(project, removeFiles = true)
                                dismiss()
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

    private val changedData: MutableMap<String, MutableMap<String, Any>> = hashMapOf()
    private lateinit var savedData: MutableMap<String, MutableMap<String, TypedString>>
    private lateinit var binding: ActivityProjectConfigBinding
    private lateinit var project: Project

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fabProjectConfig = binding.fabProjectConfig
        val configRecyclerView = binding.configRecyclerView

        val projectName = intent.getStringExtra("projectName")!!
        project = Session.projectManager.getProject(projectName)?: throw IllegalStateException("Cannot find project $projectName")
        savedData = project.configManager.getAllConfig()

        val recyclerAdapter = ParentAdapter(applicationContext) { parentId, id, view, data ->
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
            data = (commonConfigs + project.getLanguage().configObjectList + cautiousConfigs)
            saved = savedData
            notifyDataSetChanged()
        }

        fabProjectConfig.setOnClickListener { view ->
            if (recyclerAdapter.isHavingError) {
                Snackbar.make(view, "올바르지 않은 설정이 있습니다. 확인 후 다시 시도해주세요.", Snackbar.LENGTH_SHORT).show()
                fabProjectConfig.hide()
                return@setOnClickListener
            }

            project.configManager.apply {
                update(savedData)
                sync()
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

    private fun openFolderInExplorer(path: File): Boolean {
        val uri = FileProvider.getUriForFile(applicationContext, "$packageName.provider", path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri,  DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return if (intent.resolveActivityInfo(packageManager, 0) != null) {
            startActivity(intent)
            true
        } else
            false
    }
}