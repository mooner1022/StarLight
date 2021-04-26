package com.mooner.starlight.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mooner.starlight.MainActivity
import com.mooner.starlight.utils.Utils.Companion.getLogger

class HomeViewModel : ViewModel() {
    private val _text = MutableLiveData<String>()
    private val apnd = StringBuilder()

    init {
        MainActivity.setToolbarText("홈")
        
        getLogger().bindListener {
            apnd.append(it.toString()).append("\n")
            _text.value = apnd.toString()
        }
    }

    val text: LiveData<String> = _text
}