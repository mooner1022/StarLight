/*
 * BridgeApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.legacy

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import org.mozilla.javascript.ScriptableObject

@Suppress("unused")
class BridgeApi: Api<BridgeApi.Bridge>() {

    /**
     * The `Bridge` global of legacy projects: reaches the global scope of another Rhino project.
     *
     * Its members are static: use them on `Bridge` itself. Only `getScopeOf` and `isAllowed` exist; MessengerBot's
     * other `Bridge` methods, such as `callFunctionOf` and `getVariableOf`, are missing.
     *
     * @see guide:projects#calling-other-projects
     */
    class Bridge {

        companion object {

            /**
             * The global scope of the Rhino project named [scriptName], which holds its variables and functions.
             *
             * A `.js` at the end of the name is dropped, so MessengerBot's script names work. The project must be
             * compiled, or this throws. A NeonJS project has no such scope.
             *
             * @param scriptName The project's name, matched exactly, with or without `.js`.
             * @return The project's top-level scope, an `org.mozilla.javascript.ScriptableObject`.
             * @throws IllegalStateException When no project has that name.
             * @throws IllegalArgumentException When the project doesn't run on Rhino.
             */
            @JvmStatic
            fun getScopeOf(scriptName: String): ScriptableObject {
                val projectName = if (scriptName.endsWith(".js")) scriptName.dropLast(3) else scriptName
                val project = Session.projectManager.getProjectByName(projectName)
                    ?: error("Unable to find project: $projectName")
                require(project.getLanguage().id == "JS_RHINO") { "Method 'Bridge.getScopeOf()' only supports project with language 'JS_RHINO'" }
                return project.getScope() as ScriptableObject
            }

            /**
             * Whether [getScopeOf] can reach the project named [scriptName]: it exists, is compiled and runs on Rhino.
             *
             * Unlike [getScopeOf], it doesn't drop a `.js` at the end of the name.
             *
             * @param scriptName The project's name, matched exactly.
             */
            @JvmStatic
            fun isAllowed(scriptName: String): Boolean =
                Session.projectManager.getProjectByName(scriptName)?.let { proj ->
                    proj.isCompiled && proj.getLanguage().id == "JS_RHINO"
                } == true
        }
    }

    override val name: String = "Bridge"

    override val objects: List<ApiObject> = listOf(
        function {
            name = "getScopeOf"
            args = arrayOf(String::class.java)
            returns = ScriptableObject::class.java
        },
        function {
            name = "isAllowed"
            args = arrayOf(String::class.java)
            returns = Boolean::class.java
        }
    )

    override val instanceClass: Class<Bridge> = Bridge::class.java

    override val instanceType: InstanceType = InstanceType.CLASS

    override fun getInstance(project: Project): Any = Bridge::class.java
}