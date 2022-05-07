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

    class Bridge {

        companion object {

            @JvmStatic
            fun getScopeOf(scriptName: String): ScriptableObject {
                val project = Session.projectManager.getProject(scriptName)?: error("Unable to find project: $scriptName")
                require(project.getLanguage().id == "JS_RHINO") { "Method 'Bridge.getScopeOf()' only supports project with language 'JS_RHINO'" }
                return project.getScope() as ScriptableObject
            }

            @JvmStatic
            fun isAllowed(scriptName: String): Boolean = true
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