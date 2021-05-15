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
    private val _data = MutableLiveData<List<Project>>()
    val data: LiveData<List<Project>> = _data

    init {
        Session.getProjectLoader().bind { projects ->
            _data.value = projects
        }

        CoroutineScope(Dispatchers.Default).launch {
            val data = Session.getProjectLoader().loadProjects(true)
            withContext(Dispatchers.Main) {
                _data.value = data
            }
        }
    }
}