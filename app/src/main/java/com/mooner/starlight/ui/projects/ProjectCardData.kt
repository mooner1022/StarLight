package com.mooner.starlight.ui.projects

import com.mooner.starlight.plugincore.project.Languages

data class ProjectCardData(
    val name: String,
    val language: Languages,
    var isEnabled: Boolean
)
