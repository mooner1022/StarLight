package com.mooner.starlight.ui.projects

import com.mooner.starlight.plugincore.language.ILanguage
import java.io.File

data class ProjectCardData(
        val name: String,
        val language: ILanguage,
        var isEnabled: Boolean,
        val fileDir: File
)
