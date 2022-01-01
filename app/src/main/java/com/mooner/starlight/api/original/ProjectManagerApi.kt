package com.mooner.starlight.api.original

import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiFunction
import com.mooner.starlight.plugincore.api.InstanceType
import com.mooner.starlight.plugincore.project.Project

class ProjectManagerApi: Api<ProjectManagerApi.ProjectManager>() {

    class ProjectManager(
        private val project: Project
    ) {

        fun getProject(): Project = project

        fun getProject(name: String): Project? {
            return Session.projectManager.getProject(name)
        }
    }

    override val name: String = "ProjectManager"

    override val instanceType: InstanceType = InstanceType.OBJECT

    override val instanceClass: Class<ProjectManager> = ProjectManager::class.java

    override val objects: List<ApiFunction> = listOf(
        function {
            name = "getProject"
            returns = Project::class.java
        },
        function {
            name = "getProject"
            args = arrayOf(String::class.java)
            returns = Project::class.java
        }
    )

    override fun getInstance(project: Project): Any = ProjectManager(project)
}