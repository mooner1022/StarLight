package com.mooner.starlight.ui.projects.config

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession
import kotlinx.android.synthetic.main.activity_project_config.*
import kotlinx.android.synthetic.main.activity_project_config.appBarConfig
import kotlin.math.abs

class ProjectConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_config)
        val toolbar: Toolbar = findViewById(R.id.toolbarConfig)
        setSupportActionBar(toolbar)

        supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_round_arrow_left_24)
        }

        val projectName = intent.getStringExtra("projectName")!!
        val project = ApplicationSession.projectLoader.getProject(projectName)?: throw IllegalStateException("Cannot find project $projectName")
        val recyclerAdapter = ProjectConfigAdapter(applicationContext)

        recyclerAdapter.data = project.getLanguage().configList.toMutableList()
        recyclerAdapter.notifyDataSetChanged()

        val layoutManager = LinearLayoutManager(applicationContext)
        configRecyclerView.layoutManager = layoutManager
        configRecyclerView.adapter = recyclerAdapter

        val textViewConfigProjectName: TextView = findViewById(R.id.textViewConfigProjectName)
        textViewConfigProjectName.text = projectName
        appBarConfig.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                val percent = abs(
                    verticalOffset / appBarLayout.totalScrollRange
                        .toFloat()
                )
                textViewConfigProjectName.alpha = 1.0f - percent
            }
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home->{ // 메뉴 버튼
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }
}