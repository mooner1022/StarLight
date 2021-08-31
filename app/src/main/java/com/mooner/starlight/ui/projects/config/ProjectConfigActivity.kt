package com.mooner.starlight.ui.projects.config

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.R
import com.mooner.starlight.databinding.ActivityProjectConfigBinding
import com.mooner.starlight.plugincore.TypedString
import com.mooner.starlight.plugincore.config.ConfigObject
import com.mooner.starlight.plugincore.config.config
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Session.Companion.json
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.plugincore.utils.Icon
import com.mooner.starlight.utils.FileUtils
import com.mooner.starlight.utils.ViewUtils.Companion.bindFadeImage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class ProjectConfigActivity: AppCompatActivity() {

    companion object {
        private const val LANGUAGE_CONFIG_FILE_NAME = "config-language.json"
    }

    private val commonConfigs: List<ConfigObject> = config {
        title {
            title = "일반"
        }
        button {
            id = "open_folder"
            name = "폴더 열기"
            onClickListener = { view ->
                FileUtils.openFolderInExplorer(view.context, project.directory.path)
            }
            icon = Icon.FOLDER
            iconTintColor = 0xB8DFD8
        }
        toggle {
            id = "shutdown_on_error"
            name = "오류 발생시 비활성화"
            defaultValue = true
        }
    }
    private val changedData: MutableMap<String, Any> = hashMapOf()
    private lateinit var savedData: MutableMap<String, TypedString>
    private lateinit var binding: ActivityProjectConfigBinding
    private lateinit var project: Project

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fabProjectConfig = binding.fabProjectConfig
        val configRecyclerView = binding.configRecyclerView

        val projectName = intent.getStringExtra("projectName")!!
        project = Session.projectLoader.getProject(projectName)?: throw IllegalStateException("Cannot find project $projectName")
        val recyclerAdapter = ProjectConfigAdapter(applicationContext) { id, view, data ->
            changedData[id] = data
            savedData[id] = TypedString.parse(data)
            if (!fabProjectConfig.isShown) {
                fabProjectConfig.show()
            }
            project.getLanguage().onConfigChanged(id, view, data)
        }

        val configFile = File(project.directory, LANGUAGE_CONFIG_FILE_NAME)
        savedData = try {
            json.decodeFromString(configFile.readText())
        } catch (e: Exception) {
            mutableMapOf()
        }

        fabProjectConfig.setOnClickListener { view ->
            if (recyclerAdapter.isHavingError) {
                Snackbar.make(view, "올바르지 않은 설정이 있습니다. 확인 후 다시 시도해주세요.", Snackbar.LENGTH_SHORT).show()
                fabProjectConfig.hide()
                return@setOnClickListener
            }

            configFile.writeText(json.encodeToString(savedData))

            val keys = commonConfigs.map { it.id }
            if (changedData.keys.any { it !in keys }) {
                val notCommonChanged = changedData.filterNot { it.key in keys }
                project.getLanguage().onConfigUpdated(notCommonChanged)
            }
            Snackbar.make(view, "설정 저장 완료", Snackbar.LENGTH_SHORT).show()
            fabProjectConfig.hide()
        }

        binding.scroll.bindFadeImage(binding.imageViewLogo)

        binding.leave.setOnClickListener { finish() }

        recyclerAdapter.apply {
            data = (commonConfigs + project.getLanguage().configObjectList)
            saved = savedData
            notifyDataSetChanged()
        }

        val layoutManager = LinearLayoutManager(applicationContext)
        configRecyclerView.layoutManager = layoutManager
        configRecyclerView.adapter = recyclerAdapter

        val textViewConfigProjectName: TextView = findViewById(R.id.textViewConfigProjectName)
        textViewConfigProjectName.text = projectName
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home -> {
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }
}