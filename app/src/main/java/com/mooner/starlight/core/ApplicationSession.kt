package com.mooner.starlight.core

import android.content.Context
import android.os.Environment
import com.mooner.starlight.R
import com.mooner.starlight.api.core.ClientApi
import com.mooner.starlight.api.core.EventApi
import com.mooner.starlight.api.helper.*
import com.mooner.starlight.languages.JSRhino
import com.mooner.starlight.languages.JSV8
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Session.pluginLoader
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.api.ApiManager
import com.mooner.starlight.plugincore.core.Session.pluginManager
import com.mooner.starlight.plugincore.plugin.EventListener
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import com.mooner.starlight.plugincore.utils.NetworkUtil
import com.mooner.starlight.ui.widget.DummyWidgetSlim
import com.mooner.starlight.ui.widget.LogsWidget
import com.mooner.starlight.ui.widget.UptimeWidgetDefault
import com.mooner.starlight.ui.widget.UptimeWidgetSlim
import java.io.File

object ApplicationSession {
    private var mInitMillis: Long = 0L
    val initMillis: Long
        get() = mInitMillis

    var isInitComplete: Boolean = false

    internal fun init(context: Context, onPhaseChanged: (phase: String) -> Unit = {}, onFinished: () -> Unit = {}) {
        if (isInitComplete) {
            onFinished()
            return
        }

        @Suppress("DEPRECATION")
        val starlightDir = File(Environment.getExternalStorageDirectory(), "StarLight/")
        Logger.init(starlightDir)

        onPhaseChanged(context.getString(R.string.step_default_lib))
        ApiManager.apply {
            addApi(LanguagesApi())
            addApi(ProjectLoggerApi())
            addApi(ProjectsApi())
            addApi(UtilsApi())
            addApi(ClientApi())
            addApi(EventApi())
            addApi(TimerApi())
        }
        onPhaseChanged(context.getString(R.string.step_plugincore_init))

        Session.init(starlightDir)
        Session.languageManager.apply {
            addLanguage("", JSV8())
            addLanguage("", JSRhino())
            //addLanguage(GraalVMLang())
        }


        pluginLoader.loadPlugins { onPhaseChanged(String.format(context.getString(R.string.step_plugins), it)) }

        onPhaseChanged(context.getString(R.string.step_projects))
        Session.projectLoader.loadProjects()

        Session.widgetManager.apply {
            addWidget(DummyWidgetSlim())
            addWidget(UptimeWidgetDefault())
            addWidget(UptimeWidgetSlim())
            addWidget(LogsWidget())
        }

        isInitComplete = true
        onFinished()
        mInitMillis = System.currentTimeMillis()

        NetworkUtil.registerNetworkStatusListener(context)
        NetworkUtil.addOnNetworkStateChangedListener { state ->
            if (pluginManager.getPlugins().isNotEmpty()) {
                for (plugin in pluginManager.getPlugins()) {
                    (plugin as StarlightPlugin).onNetworkStateChanged(state)
                }
            }
        }
    }

    //lateinit var context: Context
    lateinit var eventListeners: List<EventListener>
}