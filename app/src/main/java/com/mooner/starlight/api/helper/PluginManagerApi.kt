package com.mooner.starlight.api.helper

import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiFunction
import com.mooner.starlight.plugincore.api.InstanceType
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import com.mooner.starlight.plugincore.project.Project

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