package com.mooner.starlight.ui.projects

import com.mooner.starlight.plugincore.language.Language
import java.io.File

data class ProjectCardData(
    val name: String,
    val language: Language,
    var isEnabled: Boolean,
    val fileDir: File
)
