package com.mooner.starlight.ui.projects

import com.mooner.starlight.plugincore.language.Languages
import java.io.File

data class ProjectCardData(
    val name: String,
    val language: Languages,
    var isEnabled: Boolean,
    val fileDir: File
)
