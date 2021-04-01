package com.mooner.starlight.ui.projects

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mooner.starlight.plugincore.getProjectLoader
import com.mooner.starlight.plugincore.project.ProjectLoader
import java.io.File

class ProjectsViewModel : ViewModel() {
    private val _data = MutableLiveData<MutableList<ProjectCardData>>()
    val data: LiveData<MutableList<ProjectCardData>> = _data

    init {
        getProjectLoader().bind { project ->
            _data.value = project.map { ProjectCardData(it.config.name, it.config.language, it.config.isEnabled, File(it.directory, it.config.mainScript)) }.toMutableList()
        }
        _data.value = getProjectLoader().loadProjects().map { ProjectCardData(it.config.name, it.config.language, it.config.isEnabled, File(it.directory, it.config.mainScript)) }.toMutableList()
    }
}