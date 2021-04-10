package com.mooner.starlight.ui.projects

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mooner.starlight.MainActivity
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.Session.Companion.getLanguageManager
import java.io.File

class ProjectsViewModel : ViewModel() {
    private val _data = MutableLiveData<MutableList<ProjectCardData>>()
    val data: LiveData<MutableList<ProjectCardData>> = _data

    init {
        MainActivity.setToolbarText("프로젝트")

        Session.getProjectLoader().bind { project ->
            _data.value = project.map { ProjectCardData(it.config.name, getLanguageManager().getLanguage(it.config.language)!!, it.config.isEnabled, File(it.directory, it.config.mainScript)) }.toMutableList()
        }
        _data.value = Session.getProjectLoader().loadProjects(true).map { ProjectCardData(it.config.name, getLanguageManager().getLanguage(it.config.language)!!, it.config.isEnabled, File(it.directory, it.config.mainScript)) }.toMutableList()
    }
}