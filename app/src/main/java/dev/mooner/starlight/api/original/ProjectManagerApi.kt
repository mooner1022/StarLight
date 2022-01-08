/*
 * ProjectManagerApi.kt created by Minki Moon(mooner1022) on 22. 1. 8. 오후 7:23
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiFunction
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project

class ProjectManagerApi: Api<ProjectManagerApi.ProjectManager>() {

    class ProjectManager(
        private val project: Project
    ) {

        fun getProject(): Project = project

        fun getProject(name: String): Project? {
            return dev.mooner.starlight.plugincore.Session.projectManager.getProject(name)
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