package com.mooner.starlight.models

import androidx.annotation.DrawableRes
import com.mooner.starlight.plugincore.project.Project

data class Message (
        val message : String,
        val roomName: String,
        var viewType : Int
)

data class Align(
        val name: String,
        val reversedName: String,
        @DrawableRes
        val icon: Int,
        val sort: (list: List<Project>, activeFirst: Boolean) -> List<Project>
)