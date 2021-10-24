package com.mooner.starlight.core

import android.content.Context
import android.os.Environment
import com.mooner.starlight.R
import com.mooner.starlight.api.core.ClientMethod
import com.mooner.starlight.api.core.EventMethod
import com.mooner.starlight.api.helper.*
import com.mooner.starlight.languages.JSRhino
import com.mooner.starlight.languages.JSV8
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Session.Companion.pluginLoader
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.method.MethodManager
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
        MethodManager.apply {
            addMethod(LanguagesMethod())
            addMethod(ProjectLoggerMethod())
            addMethod(ProjectsMethod())
            addMethod(UtilsMethod())
            addMethod(ClientMethod())
            addMethod(EventMethod())
            addMethod(TimerMethod())
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
            if (pluginLoader.getPlugins().isNotEmpty()) {
                for (plugin in pluginLoader.getPlugins()) {
                    (plugin as StarlightPlugin).onNetworkStateChanged(state)
                }
            }
        }
    }

    //lateinit var context: Context
    lateinit var eventListeners: List<EventListener>
}