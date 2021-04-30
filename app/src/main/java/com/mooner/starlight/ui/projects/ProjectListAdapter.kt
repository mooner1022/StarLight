package com.mooner.starlight.ui.projects

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.project.Project

class ProjectListAdapter(
    private val context: Context
): RecyclerView.Adapter<ProjectListViewHolder>() {
    var data = mutableListOf<Project>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectListViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.card_project_list, parent, false)
        return ProjectListViewHolder(view)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: ProjectListViewHolder, position: Int) {
        holder.bind(data[position])
    }
}