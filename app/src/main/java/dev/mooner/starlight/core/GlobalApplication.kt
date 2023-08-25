/*
 *     __. ,       .       .   ,
 *    (__ -+- _.._.|   * _ |_ -+-
 *    .__) | (_][  |___|(_][ ) |
 *                      ._|
 *        Project StarLight
 *
 * GlobalApplication.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.core

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.mooner.starlight.R
import dev.mooner.starlight.event.ApplicationEvent
import dev.mooner.starlight.logging.LogCollector
import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.ui.crash.FatalErrorActivity
import dev.mooner.starlight.ui.splash.quickstart.steps.SetPermissionFragment
import dev.mooner.starlight.utils.checkPermissions
import dev.mooner.starlight.utils.info
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.decodeFromString
import java.io.File

private typealias StartupInfo = Map<String, String>

private val LOG = LoggerFactory.logger {  }

class GlobalApplication: Application(), LifecycleEventObserver {

    private var mState: Int = STATE_UNDEFINED
    val state get() = mState

    override fun onCreate() {
        super.onCreate()
        //Logger.v(T, "Application onCreate() called")
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        mContext = applicationContext

        val pref = getSharedPreferences("general", 0)
        //pref.edit().clear().commit()
        val isInitial = pref.getBoolean("isInitial", true)

        val isPermissionsGrant = checkPermissions(SetPermissionFragment.REQUIRED_PERMISSIONS)

        if (isInitial || !isPermissionsGrant) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(this, "Android 12 이상 버전에서는 모든 파일 액세스 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data = Uri.fromParts("package", packageName, null)
            }.also(::startActivity)
        }

        checkStartupInfo()?.let { info ->
            if ("last_error" in info) {
                println("Found fatal error on last run, aborting launch..")
                val errorIntent = Intent(this, FatalErrorActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("errorMessage", info["last_error"])
                }
                //startupFile.delete()
                startActivity(errorIntent)
                return
            }
        }

        // Init LogCollector
        LogCollector

        if (!ForegroundTask.isRunning) {
            LOG.verbose { "Starting foreground task..." }
            val intent = Intent(this, ForegroundTask::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            LOG.info(R.string.log_foreground_started)
            //Logger.i(T, "Successfully started foreground task!")
        }

        ApplicationSession.init(applicationContext)
            .onEach {
                EventHandler.fireEvent(ApplicationEvent.Session.StageUpdate(it))
            }
            .onCompletion {
                EventHandler.apply {
                    on(callback = ::onProjectCreated)
                    on(callback = ::onProjectInfoUpdated)
                    on(callback = ::onProjectCompiled)
                    on<Events.Scheme.SchemeOpenEvent> {
                        LOG.info { params }
                    }
                }
                LoggerFactory.logger("System").info {
                    """
                            || Project ✦ StarLight |
                            |Running PluginCore v${Info.PLUGINCORE_VERSION}
                        """.trimMargin()
                }
            }
            .launchIn(CoroutineScope(Dispatchers.Default))
    }

    private fun onProjectCreated(event: Events.Project.Create) {
        LOG.verbose { "ProjectCreateEvent called, event= $event, project= ${event.project}" }
    }

    private fun onProjectInfoUpdated(event: Events.Project.InfoUpdate) {
        LOG.verbose { "ProjectInfoUpdateEvent called, event= $event, project= ${event.project}" }
    }

    private fun onProjectCompiled(event: Events.Project.Compile) {
        LOG.verbose { "ProjectCompileEvent called, event= $event, project= ${event.project}" }
    }

    private fun checkStartupInfo(): StartupInfo? {
        val startupFile = File(getStarLightDirectory(), "STARTUP.info")
        var startupData: StartupInfo? = null
        if (startupFile.exists() && startupFile.isFile) {
            try {
                startupData = Session.json.decodeFromString(startupFile.readText())
            } catch (e: Exception) {
                println("Failed to open startup info: $e")
                startupFile.delete()
            }
        }
        return startupData
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when(event) {
            Lifecycle.Event.ON_START -> {
                mState = STATE_FOREGROUND
                LOG.verbose { "Application state changing to STATE_FOREGROUND" }
            }
            Lifecycle.Event.ON_STOP  -> {
                mState = STATE_BACKGROUND
                LOG.verbose { "Application state changing to STATE_BACKGROUND" }
            }
            else -> {}
        }
        ApplicationEvent.Lifecycle.Update(source, event)
            .also(EventHandler::fireEventWithScope)
    }

    companion object {

        const val STATE_UNDEFINED  = -1
        const val STATE_FOREGROUND = 0
        const val STATE_BACKGROUND = 1

        @SuppressLint("StaticFieldLeak")
        @JvmStatic
        private var mContext: Context? = null
        fun requireContext(): Context = mContext!!
    }
}