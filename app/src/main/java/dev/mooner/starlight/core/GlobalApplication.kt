package dev.mooner.starlight.core

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.mooner.starlight.MainActivity
import dev.mooner.starlight.core.session.ApplicationSession
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GlobalApplication: Application() {

    companion object {
        private val T = GlobalApplication::class.simpleName

        @SuppressLint("StaticFieldLeak")
        @JvmStatic
        private var mContext: Context? = null
        fun requireContext(): Context = mContext!!
    }

    override fun onCreate() {
        super.onCreate()
        //Logger.v(T, "Application onCreate() called")

        mContext = applicationContext

        val pref = getSharedPreferences("general", 0)
        //pref.edit().clear().commit()
        val isInitial = pref.getBoolean("isInitial", true)

        if (isInitial) return

        if (!ForegroundTask.isRunning) {
            Logger.v(T, "Starting foreground task...")
            val intent = Intent(this, ForegroundTask::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            ApplicationSession.init(applicationContext)
                .collect {
                    if (it == null) {
                        Session.eventManager.on(callback = ::onProjectCreated)
                        Session.eventManager.on(callback = ::onProjectInfoUpdated)
                        Session.eventManager.on(callback = ::onProjectCompiled)
                    }
                }
        }
    }

    private fun onProjectCreated(event: Events.Project.ProjectCreateEvent) {
        Logger.v("ProjectCreateEvent called, event= $event, project= ${event.project}")
    }

    private fun onProjectInfoUpdated(event: Events.Project.ProjectInfoUpdateEvent) {
        Logger.v("ProjectInfoUpdateEvent called, event= $event, project= ${event.project}")
    }

    private fun onProjectCompiled(event: Events.Project.ProjectCompileEvent) {
        Logger.v("ProjectCompileEvent called, event= $event, project= ${event.project}")
    }
}