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
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.plugin.EventListener
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.utils.NetworkUtil
import dev.mooner.starlight.plugincore.utils.getInternalDirectory
import dev.mooner.starlight.plugincore.version.Version
import dev.mooner.starlight.ui.widget.DummyWidgetSlim
import dev.mooner.starlight.ui.widget.LogsWidget
import dev.mooner.starlight.ui.widget.UptimeWidgetDefault
import dev.mooner.starlight.ui.widget.UptimeWidgetSlim
import dev.mooner.starlight.utils.getKakaoTalkVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import java.io.File
import kotlin.system.exitProcess

private val LOG = LoggerFactory.logger {  }

object ApplicationSession {

    var kakaoTalkVersion: Version? = null
        private set

    private var mInitMillis: Long = 0L
    val initMillis get() = mInitMillis

    @JvmStatic
    private var mIsInitComplete: Boolean = false
    val isInitComplete get() = mIsInitComplete

    internal fun init(context: Context): Flow<String?> =
        flow {
            if (mIsInitComplete) {
                LOG.warn { "Rejecting re-init of ApplicationSession" }
                emit(null)
                return@flow
            }

            setExceptionHandler(context)

            emit(context.getString(R.string.step_check_kakaotalk_version))
            context.getKakaoTalkVersion()?.let {
                if (Version.check(it))
                    kakaoTalkVersion = Version.fromString(it)
                else
                    LOG.error { "카카오톡 버전 파싱에 실패했어요. (버전: ${it})" }
            }
            LOG.info { "카카오톡 버전: $kakaoTalkVersion" }

            emit(context.getString(R.string.step_plugincore_init))

            val locale = context.getString(R.string.locale_name)
                .runCatching(Locale::valueOf)
                .getOrNull() ?: Locale.ENGLISH
            LOG.debug { "Initializing with locale $locale" }

            Session
                .apply {
                    init(locale, getInternalDirectory()) }
                .let { session ->
                    session.languageManager.apply {
                        //addLanguage("", JSV8())
                        addLanguage("", JSRhino())
                        //addLanguage(GraalVMLang())
                    }

                    if (!GlobalConfig.category("plugin").getBoolean("safe_mode", false)) {
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
            LOG.wtf { "Failed to gracefully shutdown Session: ${e.localizedMessage}\ncause:\n${e.stackTrace}" }
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
                        LOG.error { "Failed to call network event for plugin '${plugin.info.id}': $e" }
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
                Stack Trace:
                
            """.trimIndent() + paramThrowable.stackTraceToString() + "\n──────────"
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