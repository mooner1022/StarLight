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
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Session.Companion.json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class ProjectConfigActivity: AppCompatActivity() {
    private val changedData: MutableMap<String, Any> = mutableMapOf()
    private lateinit var savedData: MutableMap<String, TypedString>
    private lateinit var binding: ActivityProjectConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fabProjectConfig = binding.fabProjectConfig
        val configRecyclerView = binding.configRecyclerView

        val projectName = intent.getStringExtra("projectName")!!
        val project = Session.projectLoader.getProject(projectName)?: throw IllegalStateException("Cannot find project $projectName")
        val recyclerAdapter = ProjectConfigAdapter(applicationContext) { id, view, data ->
            changedData[id] = data
            savedData[id] = TypedString.parse(data)
            if (!fabProjectConfig.isShown) {
                fabProjectConfig.show()
            }
            project.getLanguage().onConfigChanged(id, view, data)
        }

        val configFile = File(project.directory, "config-language.json")
        savedData = try {
            json.decodeFromString(configFile.readText())
        } catch (e: Exception) {
            mutableMapOf()
        }

        fabProjectConfig.setOnClickListener {
            configFile.writeText(json.encodeToString(savedData))
            project.getLanguage().onConfigUpdated(changedData)
            Snackbar.make(it, "설정 저장 완료!", Snackbar.LENGTH_SHORT).show()
            fabProjectConfig.hide()
        }

        binding.scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (scrollY in 0..200) {
                binding.imageViewLogo.alpha = 1f - (scrollY / 200.0f)
            } else {
                binding.imageViewLogo.alpha = 0f
            }
        }

        recyclerAdapter.data = project.getLanguage().configObjectList.toMutableList()
        recyclerAdapter.saved = savedData
        recyclerAdapter.notifyDataSetChanged()

        val layoutManager = LinearLayoutManager(applicationContext)
        configRecyclerView.layoutManager = layoutManager
        configRecyclerView.adapter = recyclerAdapter

        val textViewConfigProjectName: TextView = findViewById(R.id.textViewConfigProjectName)
        textViewConfigProjectName.text = projectName
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home -> { // 메뉴 버튼
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }
}