package com.mooner.starlight.ui.projects.config

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.R
import com.mooner.starlight.databinding.ActivityProjectConfigBinding
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.TypedString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs

class ProjectConfigActivity : AppCompatActivity() {
    private val changedData: MutableMap<String, Any> = mutableMapOf()
    private lateinit var savedData: MutableMap<String, TypedString>
    private lateinit var binding: ActivityProjectConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val toolbar: Toolbar = binding.toolbarConfig
        setSupportActionBar(toolbar)

        supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_round_arrow_left_24)
        }

        val fabProjectConfig = binding.fabProjectConfig
        val configRecyclerView = binding.configRecyclerView
        val appBarConfig = binding.appBarConfig

        val projectName = intent.getStringExtra("projectName")!!
        val project = Session.projectLoader.getProject(projectName)?: throw IllegalStateException("Cannot find project $projectName")
        val recyclerAdapter = ProjectConfigAdapter(applicationContext) { id, data ->
            changedData[id] = data
            savedData[id] = TypedString.parse(data)
            if (!fabProjectConfig.isShown) {
                fabProjectConfig.show()
            }
        }

        val configFile = File(project.folder, "config-language.json")
        savedData = try {
            Json.decodeFromString(configFile.readText())
        } catch (e: Exception) {
            mutableMapOf()
        }

        fabProjectConfig.setOnClickListener {
            configFile.writeText(Json.encodeToString(savedData))
            project.getLanguage().onConfigChanged(changedData)
            Snackbar.make(it, "설정 저장 완료!", Snackbar.LENGTH_SHORT).show()
            fabProjectConfig.hide()
        }

        recyclerAdapter.data = project.getLanguage().configObjectList.toMutableList()
        recyclerAdapter.saved = savedData
        recyclerAdapter.notifyDataSetChanged()

        val layoutManager = LinearLayoutManager(applicationContext)
        configRecyclerView.layoutManager = layoutManager
        configRecyclerView.adapter = recyclerAdapter

        val textViewConfigProjectName: TextView = findViewById(R.id.textViewConfigProjectName)
        textViewConfigProjectName.text = projectName
        appBarConfig.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                val percent = 1.0f - abs(
                    verticalOffset / appBarLayout.totalScrollRange
                        .toFloat()
                )
                textViewConfigProjectName.alpha = percent
                binding.imageViewConfigIcon.alpha = percent / 2.0f
            }
        )
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