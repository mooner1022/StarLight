/*
 * ProjectManagerApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiFunction
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project

@Suppress("unused")
class ProjectsApi: Api<ProjectsApi.Projects>() {

    class Projects(
        private val project: Project
    ) {

        fun getSelf(): Project = project

        fun ofName(name: String): Project? =
            Session.projectManager.getProjectByName(name)

        fun ofId(id: String): Project? =
            Session.projectManager.getProjectById(id)
    }

    override val name: String = "Projects"

    override val instanceType: InstanceType = InstanceType.OBJECT

    override val instanceClass: Class<Projects> = Projects::class.java

    override val objects: List<ApiFunction> = listOf(
        function {
            name = "getSelf"
            returns = Project::class.java
        },
        function {
            name = "ofName"
            args = arrayOf(String::class.java)
            returns = Project::class.java
        },
        function {
            name = "ofId"
            args = arrayOf(String::class.java)
            returns = Project::class.java
        }
    )

    override fun getInstance(project: Project): Any = Projects(project)
}