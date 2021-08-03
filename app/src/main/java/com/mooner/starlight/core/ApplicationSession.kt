package com.mooner.starlight.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.mooner.starlight.R
import com.mooner.starlight.languages.JSRhino
import com.mooner.starlight.languages.JSV8
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.event.EventListener
import com.mooner.starlight.plugincore.plugin.Plugin
import com.mooner.starlight.plugincore.plugin.PluginLoader
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import com.mooner.starlight.plugincore.utils.NetworkUtil
import kotlinx.serialization.json.Json

@SuppressLint("StaticFieldLeak")
object ApplicationSession {
    private val pluginLoadTime: HashMap<String, Long> = hashMapOf()

    private var mInitMillis: Long = 0L
    val initMillis: Long
        get() = mInitMillis

    var isInitComplete: Boolean = false

    private var mPluginLoader: PluginLoader? = null
    val pluginLoader: PluginLoader
        get() = mPluginLoader!!

    private var mTaskHandler: TaskHandler? = null
    val taskHandler: TaskHandler
        get() = mTaskHandler!!

    internal fun init(onPhaseChanged: (phase: String) -> Unit, onFinished: () -> Unit) {
        if (isInitComplete) {
            onFinished()
            return
        }
        onPhaseChanged(context.getString(R.string.step_default_lib))
        Session.initLanguageManager()
        onPhaseChanged(context.getString(R.string.step_lang))
        mPluginLoader = PluginLoader()
        onPhaseChanged(context.getString(R.string.step_plugin_init))
        mTaskHandler = TaskHandler()
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
        Session.projectLoader.loadProjects()
        isInitComplete = true
        onFinished()
        mInitMillis = System.currentTimeMillis()

        NetworkUtil.registerNetworkStatusListener(context)
        NetworkUtil.addOnNetworkStateChangedListener { state ->
            if (plugins.isNotEmpty()) {
                for (plugin in plugins) {
                    (plugin as StarlightPlugin).onNetworkStateChanged(state)
                }
            }
        }
    }

    lateinit var context: Context

    lateinit var plugins: List<Plugin>
    lateinit var eventListeners: List<EventListener>
}