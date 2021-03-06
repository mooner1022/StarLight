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
import dev.mooner.starlight.api.original.*
import dev.mooner.starlight.api.unused.TimerApi
import dev.mooner.starlight.languages.rhino.JSRhino
import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.pluginLoader
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.plugin.EventListener
import dev.mooner.starlight.plugincore.utils.NetworkUtil
import dev.mooner.starlight.plugincore.version.Version
import dev.mooner.starlight.ui.widget.DummyWidgetSlim
import dev.mooner.starlight.ui.widget.LogsWidget
import dev.mooner.starlight.ui.widget.UptimeWidgetDefault
import dev.mooner.starlight.ui.widget.UptimeWidgetSlim
import dev.mooner.starlight.utils.getInternalDirectory
import dev.mooner.starlight.utils.getKakaoTalkVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import java.io.File
import kotlin.system.exitProcess

object ApplicationSession {

    private var mKakaoTalkVersion: Version? = null
    val kakaoTalkVersion: Version? = null

    private var mInitMillis: Long = 0L
    val initMillis get() = mInitMillis

    @JvmStatic
    private var mIsInitComplete: Boolean = false
    val isInitComplete get() = mIsInitComplete

    internal fun init(context: Context): Flow<String?> =
        flow {
            if (mIsInitComplete) {
                Logger.w("ApplicationSession", "Rejecting re-init of ApplicationSession")
                emit(null)
                return@flow
            }

            setExceptionHandler(context)

            val starlightDir = getInternalDirectory()
            Logger.init(starlightDir)

            emit(context.getString(R.string.step_check_kakaotalk_version))
            context.getKakaoTalkVersion()?.let {
                if (Version.check(it))
                    mKakaoTalkVersion = Version.fromString(it)
                else
                    Logger.e("???????????? ?????? ????????? ???????????????. (??????: ${it})")
            }
            Logger.d("???????????? ??????: $mKakaoTalkVersion")

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
                addApi(NotificationApi())

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
                val name = "?????? ??????"
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
                    try {
                        (plugin as EventListener).onNetworkStateChanged(state)
                    } catch (e: Error) {
                        Logger.e("Failed to call network event for plugin '${plugin.info.id}': $e")
                    }
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
                *** ???????????? ????????? ??????????????????. ?????? ???????????? ???... ***
                [?????? ????????? ?????? ???????????? ??????????????????.]
                ??????????????????????????????
                StarLight v${pInfo.versionName}(build ${versionCode})
                PluginCore v${Info.PLUGINCORE_VERSION}
                Build.VERSION.SDK_INT: ${Build.VERSION.SDK_INT}
                Build.DEVICE: ${Build.DEVICE}
                thread  : ${paramThread.name}
                message : ${paramThrowable.localizedMessage}
                cause   : ${paramThrowable.cause}
                ??????????????????????????????
                stackTrace:
                
            """.trimIndent() + paramThrowable.stackTraceToString() + "\n??????????????????????????????"
            Logger.wtf("StarLight-core", errMsg)

            val startupData = Session.json.encodeToString(mapOf("last_error" to errMsg))
            try {
                File(getInternalDirectory(), "STARTUP.info").writeText(startupData)
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                shutdown()
                exitProcess(2)
            }
        }
}