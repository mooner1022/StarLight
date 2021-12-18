package com.mooner.starlight.core.session

import android.content.Context
import com.mooner.starlight.R
import com.mooner.starlight.api.helper.LanguagesApi
import com.mooner.starlight.api.helper.ProjectLoggerApi
import com.mooner.starlight.api.helper.ProjectsApi
import com.mooner.starlight.api.helper.UtilsApi
import com.mooner.starlight.api.legacy.LegacyApi
import com.mooner.starlight.languages.JSRhino
import com.mooner.starlight.languages.JSV8
import com.mooner.starlight.listener.DefaultEvent
import com.mooner.starlight.listener.legacy.LegacyEvent
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.Session.pluginLoader
import com.mooner.starlight.plugincore.Session.pluginManager
import com.mooner.starlight.plugincore.api.ApiManager
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.plugin.EventListener
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import com.mooner.starlight.plugincore.utils.NetworkUtil
import com.mooner.starlight.ui.widget.DummyWidgetSlim
import com.mooner.starlight.ui.widget.LogsWidget
import com.mooner.starlight.ui.widget.UptimeWidgetDefault
import com.mooner.starlight.ui.widget.UptimeWidgetSlim
import com.mooner.starlight.utils.FileUtils

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

        onPhaseChanged(context.getString(R.string.step_default_lib))
        ApiManager.apply {
            addApi(LanguagesApi())
            addApi(ProjectLoggerApi())
            addApi(ProjectsApi())
            addApi(UtilsApi())
            addApi(LegacyApi())
            //addApi(ClientApi())
            //addApi(EventApi())
            //addApi(TimerApi())
        }

        onPhaseChanged(context.getString(R.string.step_plugincore_init))
        Session.init(starlightDir)
        Session.languageManager.apply {
            addLanguage("", JSV8())
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

        Session.widgetManager.apply {
            addWidget(DummyWidgetSlim())
            addWidget(UptimeWidgetDefault())
            addWidget(UptimeWidgetSlim())
            addWidget(LogsWidget())
        }

        NetworkUtil.registerNetworkStatusListener(context)
        NetworkUtil.addOnNetworkStateChangedListener { state ->
            if (pluginManager.getPlugins().isNotEmpty()) {
                for (plugin in pluginManager.getPlugins()) {
                    (plugin as StarlightPlugin).onNetworkStateChanged(state)
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