package com.mooner.starlight.core

import android.annotation.SuppressLint
import android.content.Context
import com.mooner.starlight.R
import com.mooner.starlight.languages.JSRhino
import com.mooner.starlight.languages.JSV8
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.logger.LogType
import com.mooner.starlight.plugincore.plugin.Plugin
import com.mooner.starlight.plugincore.plugin.PluginLoader
import com.mooner.starlight.plugincore.utils.NetworkUtil
import com.mooner.starlight.utils.Alert

@SuppressLint("StaticFieldLeak")
object ApplicationSession {
    private val pluginLoadTime: HashMap<String, Long> = hashMapOf()
    var isInitComplete: Boolean = false
    private var l_pluginLoader: PluginLoader? = null
    val pluginLoader: PluginLoader
        get() = l_pluginLoader!!

    private var l_taskHandler: TaskHandler? = null
    val taskHandler: TaskHandler
        get() = l_taskHandler!!

    private val initCompleteListeners: ArrayList<() -> Unit> = arrayListOf()

    fun onInitComplete(listener: () -> Unit) {
        initCompleteListeners.add(listener)
    }

    internal fun init(onPhaseChanged: (phase: String) -> Unit, onFinished: () -> Unit) {
        if (isInitComplete) {
            onFinished()
            return
        }
        onPhaseChanged(context.getString(R.string.step_default_lib))
        Session.initLanguageManager()
        onPhaseChanged(context.getString(R.string.step_lang))
        l_pluginLoader = PluginLoader()
        onPhaseChanged(context.getString(R.string.step_plugin_init))
        l_taskHandler = TaskHandler()
        Session.getLanguageManager().apply {
            addLanguage(JSV8())
            addLanguage(JSRhino())
        }
        Session.initProjectLoader()
        var preTime: Long = 0
        var preName = ""
        plugins = pluginLoader.loadPlugins {
            if (preTime != 0L) {
                pluginLoadTime[preName] = System.currentTimeMillis() - preTime
            }
            preTime = System.currentTimeMillis()
            onPhaseChanged(String.format(context.getString(R.string.step_plugins), it))
            preName = it
        }
        pluginLoadTime[preName] = System.currentTimeMillis() - preTime

        onPhaseChanged(context.getString(R.string.step_projects))
        Session.getProjectLoader().loadProjects()
        isInitComplete = true
        onFinished()
        if (initCompleteListeners.isNotEmpty()) {
            for (listener in initCompleteListeners) {
                listener()
            }
            initCompleteListeners.clear()
        }

        NetworkUtil.registerNetworkStatusListener(context)
        NetworkUtil.addOnNetworkStateChangedListener { state ->
            if (plugins.isNotEmpty()) {
                for (plugin in plugins) {
                    plugin.onNetworkStateChanged(state)
                }
            }
        }
    }

    lateinit var context: Context

    lateinit var plugins: List<Plugin>
}