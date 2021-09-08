package com.mooner.starlight.api.original

import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.method.Method
import com.mooner.starlight.plugincore.method.MethodFunction
import com.mooner.starlight.plugincore.project.Project

class Projects: Method() {

    fun get(): Project = project

    fun get(name: String): Project? {
        return Session.projectLoader.getProject(name)
    }

    override val name: String = "Projects"

    override val instance: Any
        get() = Projects()

    override val functions: List<MethodFunction> = listOf(
        MethodFunction(
            name = "get",
            args = arrayOf(String::class.java)
        )
    )
}