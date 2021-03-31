package com.mooner.starlight.ui.projects

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mooner.starlight.plugincore.project.ProjectLoader

class ProjectsViewModel : ViewModel() {
    private val _data = MutableLiveData<MutableList<ProjectCardData>>()
    val data: LiveData<MutableList<ProjectCardData>> = _data

    init {
        val projectLoader = ProjectLoader()
        _data.value = projectLoader.loadProjects().map { ProjectCardData(it.config.name, it.config.language, it.config.isEnabled) }.toMutableList()
    }
}