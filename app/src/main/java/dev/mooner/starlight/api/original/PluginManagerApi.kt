/*
 * PluginManagerApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiFunction
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.plugin.StarlightPlugin
import dev.mooner.starlight.plugincore.project.Project

@Suppress("unused")
class PluginManagerApi: Api<PluginManagerApi.PluginManager>() {

    class PluginManager {
        fun getPluginById(id: String): StarlightPlugin? {
            return Session.pluginManager.getPluginById(id)
        }

        fun getPluginByName(name: String): StarlightPlugin? {
            return Session.pluginManager.getPluginByName(name)
        }

        fun getPlugins(): List<StarlightPlugin> {
            return Session.pluginManager.getPlugins()
        }
    }

    override val name: String = "PluginManager"

    override val instanceType: InstanceType = InstanceType.OBJECT

    override val instanceClass: Class<PluginManager> = PluginManager::class.java

    override val objects: List<ApiFunction> = listOf(
        function {
            name = "getPluginById"
            args = arrayOf(String::class.java)
            returns = StarlightPlugin::class.java
        },
        function {
            name = "getPluginByName"
            args = arrayOf(String::class.java)
            returns = StarlightPlugin::class.java
        },
        function {
            name = "getPlugins"
            returns = List::class.java
        }
    )

    override fun getInstance(project: Project): Any = PluginManager()
}