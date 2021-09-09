package com.mooner.starlight.plugincore.method

import com.mooner.starlight.plugincore.project.Project

abstract class Method: IMethod {

    private var _project: Project? = null

    protected val project: Project
        get() = _project!!

    internal fun setProject(project: Project) {
        _project = project
    }
}