package com.mooner.starlight.ui.projects.info

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mooner.starlight.databinding.ActivityProjectInfoBinding
import com.mooner.starlight.plugincore.config.config
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.utils.Icon
import com.mooner.starlight.plugincore.utils.TimeUtils
import com.mooner.starlight.ui.config.ParentAdapter
import com.mooner.starlight.utils.ViewUtils.Companion.bindFadeImage

class ProjectInfoActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PROJECT_NAME = "projectName"
    }

    private lateinit var binding: ActivityProjectInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME)!!
        val project = (Session.projectManager.getProject(projectName)?: error("Failed to get project [$projectName]"))
        val lang = project.getLanguage()
        val recyclerAdapter = ParentAdapter(applicationContext) { _, _, _, _ -> }

        val info = project.info
        recyclerAdapter.data = config {
            category {
                id = "general"
                title = "기본"
                items = items {
                    button {
                        id = "name"
                        title = info.name
                        icon = Icon.PROJECTS
                        iconTintColor = color { "#316B83" }
                        onClickListener = {}
                    }
                    button {
                        id = "mainScript"
                        title = "메인 스크립트"
                        icon = Icon.EXIT_TO_APP
                        iconTintColor = color { "#F4D19B" }
                        description = info.mainScript
                        onClickListener = {}
                    }
                    button {
                        id = "birth"
                        title = "생성일"
                        icon = Icon.ADD
                        iconTintColor = color { "#BEAEE2" }
                        description = TimeUtils.formatMillis(info.createdMillis, "yyyy/MM/dd HH:mm:ss")
                        onClickListener = {}
                    }
                    button {
                        id = "listeners"
                        title = "리스너"
                        icon = Icon.ARROW_LEFT
                        iconTintColor = color { "#98DDCA" }
                        description = info.listeners.joinToString()
                        onClickListener = {}
                    }
                    button {
                        id = "plugins"
                        title = "플러그인"
                        icon = Icon.LAYERS
                        iconTintColor = color { "#70AF85" }
                        description = info.pluginIds.joinToString()
                        onClickListener = {}
                    }
                    button {
                        id = "packages"
                        title = "패키지"
                        icon = Icon.DEVELOPER_BOARD
                        iconTintColor = color { "#ff9966" }
                        description = info.packages.joinToString()
                        onClickListener = {}
                    }
                }
            }
            category {
                id = "lang"
                title = "언어"
                items = items {
                    button {
                        id = "name"
                        title = lang.name
                        description = lang.id
                        icon = Icon.DEVELOPER_MODE
                        iconTintColor = color { "#4568DC" }
                        onClickListener = {}
                    }
                    button {
                        id = "fileExt"
                        title = "파일 확장자"
                        icon = Icon.FOLDER
                        iconTintColor = color { "#7A69C7" }
                        description = lang.fileExtension
                        onClickListener = {}
                    }
                    button {
                        id = "reqRelease"
                        title = "릴리스 필요"
                        icon = Icon.DELETE_SWEEP
                        iconTintColor = color { "#B06AB3" }
                        description = lang.requireRelease.toString()
                        onClickListener = {}
                    }
                }
            }
            category {
                id = "state"
                title = "상태"
                items = items {
                    button {
                        id = "threadName"
                        title = "스레드"
                        icon = Icon.LAYERS
                        iconTintColor = color { "#3A1C71" }
                        description = project.threadName?: "할당되지 않음"
                        onClickListener = {}
                    }
                    button {
                        id = "isCompiled"
                        title = "컴파일"
                        icon = Icon.REFRESH
                        iconTintColor = color { "#D76D77" }
                        description = project.isCompiled.toString()
                        onClickListener = {}
                    }
                    button {
                        id = "isEnabled"
                        title = "활성화"
                        icon = Icon.EDIT_ATTRIBUTES
                        iconTintColor = color { "#FFAF7B" }
                        description = info.isEnabled.toString()
                        onClickListener = {}
                    }
                }
            }
        }
        recyclerAdapter.notifyDataSetChanged()

        binding.leave.setOnClickListener { finish() }

        binding.scroll.bindFadeImage(binding.imageViewLogo)

        val layoutManager = LinearLayoutManager(applicationContext)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = recyclerAdapter

        binding.projectName.text = projectName
    }
}