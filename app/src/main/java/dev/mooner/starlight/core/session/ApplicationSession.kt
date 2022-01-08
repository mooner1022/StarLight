/*
 * ApplicationSession.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.core.session

import android.content.Context
import dev.mooner.starlight.R
import dev.mooner.starlight.api.api2.AppApi
import dev.mooner.starlight.api.legacy.*
import dev.mooner.starlight.api.original.LanguageManagerApi
import dev.mooner.starlight.api.original.PluginManagerApi
import dev.mooner.starlight.api.original.ProjectLoggerApi
import dev.mooner.starlight.api.original.ProjectManagerApi
import dev.mooner.starlight.api.unused.TimerApi
import dev.mooner.starlight.languages.JSRhino
import dev.mooner.starlight.listener.DefaultEvent
import dev.mooner.starlight.listener.legacy.LegacyEvent
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.pluginLoader
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.plugin.EventListener
import dev.mooner.starlight.plugincore.utils.NetworkUtil
import dev.mooner.starlight.ui.widget.DummyWidgetSlim
import dev.mooner.starlight.ui.widget.LogsWidget
import dev.mooner.starlight.ui.widget.UptimeWidgetDefault
import dev.mooner.starlight.ui.widget.UptimeWidgetSlim
import dev.mooner.starlight.utils.FileUtils

object ApplicationSession {
    private var mInitMillis: Long = 0L
    val initMillis get() = mInitMillis

    var isInitComplete: Boolean = false
    @JvmStatic
    var isAfterInit: Boolean = false

    private val listeners: MutableSet<SessionInitListener> = hashSetOf()

    fun setOnInitListener(listener: SessionInitListener) {
        if (isInitComplete) listener.onFinished()
        else listeners += listener
    }

    internal fun init(context: Context) {
        if (isInitComplete) {
            onFinished()
            return
        }
        if (isAfterInit) {
            Logger.w("ApplicationSession", "Rejecting re-init of ApplicationSession")
            return
        }
        isAfterInit = true

        val starlightDir = FileUtils.getInternalDirectory()
        Logger.init(starlightDir)

        onPhaseChanged(context.getString(R.string.step_plugincore_init))
        Session.init(starlightDir)
        Session.languageManager.apply {
            //addLanguage("", JSV8())
            addLanguage("", JSRhino())
            //addLanguage(GraalVMLang())
        }

        Session.eventManager.apply {
            addEvent(DefaultEvent())
            addEvent(LegacyEvent())
        }

        if (!Session.globalConfig.getCategory("plugin").getBoolean("safe_mode", false)) {
            pluginLoader.loadPlugins { onPhaseChanged(String.format(context.getString(R.string.step_plugins), it)) }
        } else {
            Logger.i("PluginLoader", "Skipping plugin load...")
        }

        onPhaseChanged(context.getString(R.string.step_projects))
        Session.projectLoader.loadProjects()

        onPhaseChanged(context.getString(R.string.step_default_lib))
        Session.apiManager.apply {
            // Original Apis
            addApi(LanguageManagerApi())
            addApi(ProjectLoggerApi())
            addApi(ProjectManagerApi())
            addApi(PluginManagerApi())
            addApi(TimerApi())

            // Legacy Apis
            addApi(UtilsApi())
            addApi(LegacyApi())
            addApi(BridgeApi())
            addApi(FileStreamApi())
            addApi(DataBaseApi())
            addApi(DeviceApi())

            // Api2 Apis
            addApi(AppApi())

            //addApi(ClientApi())
            //addApi(EventApi())
            //addApi(TimerApi())
        }

        Session.widgetManager.apply {
            val name = "기본 위젯"
            addWidget(name, DummyWidgetSlim())
            addWidget(name, UptimeWidgetDefault())
            addWidget(name, UptimeWidgetSlim())
            addWidget(name, LogsWidget())
        }

        NetworkUtil.registerNetworkStatusListener(context)
        NetworkUtil.addOnNetworkStateChangedListener { state ->
            if (pluginManager.getPlugins().isNotEmpty()) {
                for (plugin in pluginManager.getPlugins()) {
                    plugin.onNetworkStateChanged(state)
                }
            }
        }

        isInitComplete = true
        onFinished()
        listeners.clear()
        mInitMillis = System.currentTimeMillis()
    }

    internal fun shutdown() {
        try {
            Session.shutdown()
        } catch (e: Exception) {
            Logger.wtf("ApplicationSession", "Failed to gracefully shutdown Session: ${e.localizedMessage}\ncause:\n${e.stackTrace}")
        }
    }

    private fun onFinished() {
        for (listener in listeners) listener.onFinished()
    }

    private fun onPhaseChanged(phase: String) {
        for (listener in listeners) listener.onPhaseChanged(phase)
    }

    //lateinit var context: Context
    lateinit var eventListeners: List<EventListener>
}