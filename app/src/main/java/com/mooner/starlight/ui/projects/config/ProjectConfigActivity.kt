package com.mooner.starlight.ui.projects.config

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession
import kotlinx.android.synthetic.main.activity_project_config.*
import kotlinx.android.synthetic.main.fragment_projects.view.*

class ProjectConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_config)

        val projectName = intent.getStringExtra("projectName")!!
        title = projectName
        val project = ApplicationSession.projectLoader.getProject(projectName)?: throw IllegalStateException("Cannot find project $projectName")
        val recyclerAdapter = ProjectConfigAdapter(applicationContext)

        recyclerAdapter.data = project.getLanguage().configList.toMutableList()
        recyclerAdapter.notifyDataSetChanged()

        val layoutManager = LinearLayoutManager(applicationContext)
        configRecyclerView.layoutManager = layoutManager
        configRecyclerView.adapter = recyclerAdapter
    }
}