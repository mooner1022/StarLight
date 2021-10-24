package com.mooner.starlight.api.helper

import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.method.Method
import com.mooner.starlight.plugincore.method.MethodFunction
import com.mooner.starlight.plugincore.method.MethodType
import com.mooner.starlight.plugincore.project.Project

class ProjectsMethod: Method<ProjectsMethod.Projects>() {

    class Projects(
        private val project: Project
    ) {

        fun get(): Project = project

        fun get(name: String): Project? {
            return Session.projectManager.getProject(name)
        }
    }

    override val name: String = "Projects"

    override val type: MethodType = MethodType.OBJECT

    override val instanceClass: Class<Projects> = Projects::class.java

    override val functions: List<MethodFunction> = listOf(
        function {
            name = "get"
            returns = Project::class.java
        },
        function {
            name = "get"
            args = arrayOf(String::class.java)
            returns = Project::class.java
        }
    )

    override fun getInstance(project: Project): Any = Projects(project)
}