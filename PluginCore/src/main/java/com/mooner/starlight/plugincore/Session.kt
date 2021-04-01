package com.mooner.starlight.plugincore

import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.project.ProjectLoader

internal object Session {
    val logger = Logger()
    val projectLoader: ProjectLoader = ProjectLoader()

}

fun getLogger(): Logger = Session.logger

fun getProjectLoader(): ProjectLoader = Session.projectLoader