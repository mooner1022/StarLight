/*
 * ApplicationSession.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.core.session

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import dev.mooner.starlight.R
import dev.mooner.starlight.api.api2.AppApi
import dev.mooner.starlight.api.legacy.*
import dev.mooner.starlight.api.original.LanguageManagerApi
import dev.mooner.starlight.api.original.PluginManagerApi
import dev.mooner.starlight.api.original.ProjectLoggerApi
import dev.mooner.starlight.api.original.ProjectManagerApi
import dev.mooner.starlight.api.unused.TimerApi
import dev.mooner.starlight.languages.JSRhino
import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.pluginLoader
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.utils.NetworkUtil
import dev.mooner.starlight.ui.widget.DummyWidgetSlim
import dev.mooner.starlight.ui.widget.LogsWidget
import dev.mooner.starlight.ui.widget.UptimeWidgetDefault
import dev.mooner.starlight.ui.widget.UptimeWidgetSlim
import dev.mooner.starlight.utils.getInternalDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import java.io.File
import kotlin.system.exitProcess

object ApplicationSession {
    private var mInitMillis: Long = 0L
    val initMillis get() = mInitMillis

    @JvmStatic
    private var mIsInitComplete: Boolean = false
    val isInitComplete get() = mIsInitComplete
    @JvmStatic
    private var isAfterInit: Boolean = false

    internal fun init(context: Context): Flow<String?> =
        flow {
            if (mIsInitComplete) {
                emit(null)
                return@flow
            }
            if (isAfterInit) {
                Logger.w("ApplicationSession", "Rejecting re-init of ApplicationSession")
                emit(null)
                return@flow
            }
            isAfterInit = true

            setExceptionHandler(context)

            val starlightDir = getInternalDirectory()
            Logger.init(starlightDir)

            emit(context.getString(R.string.step_plugincore_init))

            Session
                .apply { init(starlightDir) }
                .let { session ->
                    session.languageManager.apply {
                        //addLanguage("", JSV8())
                        addLanguage("", JSRhino())
                        //addLanguage(GraalVMLang())
                    }

                    if (!session.globalConfig.category("plugin").getBoolean("safe_mode", false)) {
                        pluginLoader.loadPlugins()
                            .flowOn(Dispatchers.Default)
                            .onEach { value ->
                                if (value is String)
                                    emit(context.getString(R.string.step_plugins).format(value))
                            }
                            .collect()
                    } else {
                        Logger.i("PluginLoader", "Skipping plugin load...")
                    }
                }

            emit(context.getString(R.string.step_default_lib))
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
            }

            Session.widgetManager.apply {
                val name = "기본 위젯"
                addWidget(name, DummyWidgetSlim())
                addWidget(name, UptimeWidgetDefault())
                addWidget(name, UptimeWidgetSlim())
                addWidget(name, LogsWidget())
            }

            emit(context.getString(R.string.step_projects))
            Session.projectLoader.loadProjects()

            setNetworkHandler(context)

            mIsInitComplete = true
            emit(null)
            mInitMillis = System.currentTimeMillis()
        }

    internal fun shutdown() {
        try {
            Session.shutdown()
        } catch (e: Exception) {
            Logger.wtf("ApplicationSession", "Failed to gracefully shutdown Session: ${e.localizedMessage}\ncause:\n${e.stackTrace}")
        }
    }

    private fun setNetworkHandler(context: Context) {
        NetworkUtil.registerNetworkStatusListener(context)
        NetworkUtil.addOnNetworkStateChangedListener { state ->
            if (pluginManager.getPlugins().isNotEmpty()) {
                for (plugin in pluginManager.getPlugins()) {
                    plugin.onNetworkStateChanged(state)
                }
            }
        }
    }

    private fun setExceptionHandler(context: Context) =
        Thread.setDefaultUncaughtExceptionHandler { paramThread, paramThrowable ->
            val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                pInfo.longVersionCode
            else
                pInfo.versionCode

            val errMsg = """
                *** 치명적인 오류가 발생했습니다. 앱을 종료하는 중... ***
                [버그 제보시 아래 메세지를 첨부해주세요.]
                ──────────
                StarLight v${pInfo.versionName}(build ${versionCode})
                PluginCore v${Info.PLUGINCORE_VERSION}
                Build.VERSION.SDK_INT: ${Build.VERSION.SDK_INT}
                Build.DEVICE: ${Build.DEVICE}
                thread  : ${paramThread.name}
                message : ${paramThrowable.localizedMessage}
                cause   : ${paramThrowable.cause}
                ┉┉┉┉┉┉┉┉┉┉
                stackTrace:
                
            """.trimIndent() + paramThrowable.stackTraceToString() + "\n──────────"
            Logger.wtf("StarLight-core", errMsg)

            val startupData = Session.json.encodeToString(mapOf("last_error" to errMsg))
            File(getInternalDirectory(), "STARTUP.info").writeText(startupData)
            shutdown()
            exitProcess(2)
        }
}