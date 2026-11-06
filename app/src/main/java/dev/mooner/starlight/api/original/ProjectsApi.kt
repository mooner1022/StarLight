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

    /**
     * StarLight's projects: this one, or another by name or ID, mostly to call another project's functions.
     *
     * A project is a `Project` (`describe_api Project`). Scripts mostly call its functions with
     * `callFunction(name, args, onError)`, which takes all three arguments, and read its `info.name` and `compiled`:
     * ```js
     * const other = Projects.ofName("scheduler");
     * if (other) other.callFunction("addReminder", ["room", "text"], (e) => Log.e("scheduler", String(e)));
     * ```
     * The call runs on that project's workers and doesn't return the function's result. On a project that isn't
     * compiled, `onError` is called instead; so is it for a project calling its own functions from its top-level code,
     * which runs before its compile finishes.
     *
     * @see guide:projects#calling-other-projects
     */
    class Projects(
        private val project: Project
    ) {

        /** The project running this script. */
        fun getSelf(): Project = project

        /**
         * The project named [name], or `null` if there is none.
         *
         * The project may be off, or not compiled: check its `compiled` before relying on its functions.
         *
         * @param name The project's name, compared exactly, case included.
         */
        fun ofName(name: String): Project? =
            Session.projectManager.getProjectByName(name)

        /**
         * The project with the ID [id], or `null` if there is none.
         *
         * @param id The project's ID, the UUID in its `project.json`.
         * @throws IllegalArgumentException If [id] isn't a UUID.
         */
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