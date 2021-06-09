package com.mooner.starlight.plugincore.methods.original

import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.project.Project

class Projects {
    companion object {
        fun get(name: String): Project? {
            return Session.projectLoader.getProject(name)
        }
    }
}