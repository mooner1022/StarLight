package com.mooner.starlight.core

import android.annotation.SuppressLint
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
import com.mooner.starlight.plugincore.plugin.EventListener
import com.mooner.starlight.plugincore.method.MethodManager
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import com.mooner.starlight.plugincore.utils.NetworkUtil
import java.io.File

@SuppressLint("StaticFieldLeak")
object ApplicationSession {
    private val pluginLoadTime: HashMap<String, Long> = hashMapOf()

    private var mInitMillis: Long = 0L
    val initMillis: Long
        get() = mInitMillis

    var isInitComplete: Boolean = false

    internal fun init(onPhaseChanged: (phase: String) -> Unit, onFinished: () -> Unit) {
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
            val dummyPath = File(starlightDir, "").path
            addLanguage(dummyPath, JSV8())
            addLanguage(dummyPath, JSRhino())
            //addLanguage(GraalVMLang())
        }

        var preTime: Long = 0
        var preName = ""
        pluginLoader.loadPlugins {
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
            if (pluginLoader.getPlugins().isNotEmpty()) {
                for (plugin in pluginLoader.getPlugins()) {
                    (plugin as StarlightPlugin).onNetworkStateChanged(state)
                }
            }
        }
    }

    lateinit var context: Context
    lateinit var eventListeners: List<EventListener>
}