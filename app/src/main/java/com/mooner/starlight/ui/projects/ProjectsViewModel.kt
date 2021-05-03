package com.mooner.starlight.ui.projects

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mooner.starlight.MainActivity
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectsViewModel : ViewModel() {
    private val _data = MutableLiveData<MutableList<Project>>()
    val data: LiveData<MutableList<Project>> = _data

    init {
        Session.getProjectLoader().bind { projects ->
            _data.value = projects.toMutableList()
        }

        CoroutineScope(Dispatchers.Default).launch {
            val data = Session.getProjectLoader().loadProjects(true).toMutableList()
            withContext(Dispatchers.Main) {
                _data.value = data
            }
        }
    }
}