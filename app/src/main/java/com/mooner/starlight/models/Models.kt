package com.mooner.starlight.models

import androidx.annotation.DrawableRes
import com.mooner.starlight.plugincore.project.Project

data class Message (
        val message : String,
        val roomName: String,
        var viewType : Int
)

data class Align <T>(
        val name: String,
        val reversedName: String,
        @DrawableRes
        val icon: Int,
        val sort: (list: List<T>, args: Map<String, Boolean>) -> List<T>
)