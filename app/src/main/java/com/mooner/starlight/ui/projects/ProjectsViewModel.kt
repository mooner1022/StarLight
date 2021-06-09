package com.mooner.starlight.ui.projects

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectsViewModel : ViewModel() {
    private val _data = MutableLiveData<List<Project>>()
    val data: LiveData<List<Project>> = _data

    init {
        Session.projectLoader.bind { projects ->
            _data.value = projects
        }

        CoroutineScope(Dispatchers.Default).launch {
            val data = Session.projectLoader.loadProjects(false)
            withContext(Dispatchers.Main) {
                _data.value = data
            }
        }
    }
}