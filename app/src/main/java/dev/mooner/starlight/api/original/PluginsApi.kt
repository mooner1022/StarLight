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
class PluginsApi: Api<PluginsApi.Plugins>() {

    class Plugins {
        fun ofId(id: String): StarlightPlugin? {
            return Session.pluginManager.getPluginById(id)
        }

        fun ofName(name: String): StarlightPlugin? {
            return Session.pluginManager.getPluginByName(name)
        }

        fun getAll(): List<StarlightPlugin> =
            Session.pluginManager.plugins.toList()
    }

    override val name: String = "Plugins"

    override val instanceType: InstanceType = InstanceType.OBJECT

    override val instanceClass: Class<Plugins> = Plugins::class.java

    override val objects: List<ApiFunction> = listOf(
        function {
            name = "ofId"
            args = arrayOf(String::class.java)
            returns = StarlightPlugin::class.java
        },
        function {
            name = "ofName"
            args = arrayOf(String::class.java)
            returns = StarlightPlugin::class.java
        },
        function {
            name = "getAll"
            returns = List::class.java
        }
    )

    override fun getInstance(project: Project): Any = Plugins()
}