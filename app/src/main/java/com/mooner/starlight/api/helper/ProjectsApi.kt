package com.mooner.starlight.api.helper

import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiFunction
import com.mooner.starlight.plugincore.api.InstanceType
import com.mooner.starlight.plugincore.project.Project

class ProjectsApi: Api<ProjectsApi.Projects>() {

    class Projects(
        private val project: Project
    ) {

        fun get(): Project = project

        fun get(name: String): Project? {
            return Session.projectManager.getProject(name)
        }
    }

    override val name: String = "Projects"

    override val instanceType: InstanceType = InstanceType.OBJECT

    override val instanceClass: Class<Projects> = Projects::class.java

    override val objects: List<ApiFunction> = listOf(
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