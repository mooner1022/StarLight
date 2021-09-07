package com.mooner.starlight.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.webkit.WebView
import com.mooner.starlight.R
import com.mooner.starlight.languages.JSRhino
import com.mooner.starlight.languages.JSV8
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Session.Companion.pluginLoader
import com.mooner.starlight.plugincore.event.EventListener
import com.mooner.starlight.api.legacy.LegacyApis
import com.mooner.starlight.api.original.OriginalApis
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import com.mooner.starlight.plugincore.utils.NetworkUtil
import com.mooner.starlight.ui.editor.EditorActivity

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
        LegacyApis().init()
        OriginalApis().init()
        onPhaseChanged(context.getString(R.string.step_default_lib))
        Session.initLanguageManager()
        onPhaseChanged(context.getString(R.string.step_lang))
        Session.initPluginLoader()
        onPhaseChanged(context.getString(R.string.step_plugin_init))
        Session.getLanguageManager().apply {
            addLanguage(JSV8())
            addLanguage(JSRhino())
            //addLanguage(GraalVMLang())
        }
        Session.initProjectLoader()
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