package com.mooner.starlight.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mooner.starlight.plugincore.Session

class HomeViewModel : ViewModel() {
    private val _text = MutableLiveData<String>()
    val apnd = StringBuilder()

    init {
        Session.logger.bind {
            apnd.append(it.toString()).append("\n")
            _text.value = apnd.toString()
        }
    }

    val text: LiveData<String> = _text
}