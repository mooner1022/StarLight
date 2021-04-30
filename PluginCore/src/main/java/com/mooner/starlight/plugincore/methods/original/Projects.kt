package com.mooner.starlight.plugincore.methods.original

import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.project.Project

class Projects {
    companion object {
        fun get(name: String): Project? {
            return Session.getProjectLoader().getProject(name)
        }
    }
}