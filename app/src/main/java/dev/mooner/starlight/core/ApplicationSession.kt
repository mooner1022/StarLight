/*
 * ApplicationSession.kt created by Minki Moon(mooner1022) on 4/25/23, 1:12 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.core

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import dev.mooner.configdsl.ConfigDSL
import dev.mooner.starlight.CA_PLUGIN
import dev.mooner.starlight.CF_SAFE_MODE
import dev.mooner.starlight.R
import dev.mooner.starlight.api.api2.AppApi
import dev.mooner.starlight.api.api2.BroadcastApi
import dev.mooner.starlight.api.legacy.*
import dev.mooner.starlight.api.node.EventEmitterApi
import dev.mooner.starlight.api.original.*
import dev.mooner.starlight.languages.rhino.JSRhino
import dev.mooner.starlight.listener.event.*
import dev.mooner.starlight.listener.specs.AndroidRParserSpec
import dev.mooner.starlight.listener.specs.DefaultParserSpec
import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.chat.ParserSpecManager
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.plugin.PluginContext
import dev.mooner.starlight.plugincore.project.event.ProjectEventBuilder
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.utils.NetworkUtil
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.plugincore.version.Version
import dev.mooner.starlight.ui.config.ParentConfigAdapterImpl
import dev.mooner.starlight.ui.widget.*
import dev.mooner.starlight.utils.getKakaoTalkVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import java.io.File
import kotlin.system.exitProcess

private val logger = LoggerFactory.logger {  }

object ApplicationSession {

    var kakaoTalkVersion: Version? = null
        private set

    private var mInitMillis: Long = 0L
    val initMillis get() = mInitMillis

    @JvmStatic
    var initState: InitState = InitState.None
        private set

    val isInitComplete get() = initState == InitState.Done

    internal fun init(context: Context): Flow<String?> =
        flow {
            initState = InitState.Running
            if (isInitComplete) {
                logger.warn { "Rejecting re-init of ApplicationSession" }
                initState = InitState.Done
                emit(null)
                return@flow
            }

            setExceptionHandler(context)

            emit(context.getString(R.string.step_check_kakaotalk_version))
            context.getKakaoTalkVersion()?.let { version ->
                if (Version.check(version))
                    kakaoTalkVersion = Version.fromString(version)
                else
                    logger.error { "카카오톡 버전 파싱에 실패했어요. (버전: ${version})" }
            }
            logger.info { "카카오톡 버전: $kakaoTalkVersion" }

            emit(context.getString(R.string.step_plugincore_init))

            val locale = context.getString(R.string.locale_name)
                .runCatching(Locale::valueOf)
                .getOrNull() ?: Locale.ENGLISH
            logger.debug { "Initializing with locale $locale" }

            val pContext = Session.init(locale, getStarLightDirectory()) ?: return@flow

            Session.languageManager.apply {
                //addLanguage("", JSV8())
                addLanguage(pContext, JSRhino())
                //addLanguage(GraalVMLang())
            }

            if (!GlobalConfig.category(CA_PLUGIN).getBoolean(CF_SAFE_MODE, false)) {
                Session.pluginLoader.loadPlugins(context)
                    .flowOn(Dispatchers.Default)
                    .onEach { value ->
                        if (value is String)
                            emit(context.getString(R.string.step_plugins).format(value))
                    }
                    .collect()
            } else {
                logger.info { "Skipping plugin load..." }
            }

            emit(context.getString(R.string.step_default_lib))
            //CorePlugin()

            ConfigDSL.registerAdapterImpl(::ParentConfigAdapterImpl)

            ParserSpecManager.apply {
                registerSpec(DefaultParserSpec())
                registerSpec(AndroidRParserSpec())
            }

            Session.apiManager.apply {
                // Original Apis
                addApi(LanguageManagerApi())
                addApi(ProjectLoggerApi())
                addApi(ProjectManagerApi())
                addApi(PluginManagerApi())
                //addApi(TimerApi())
                addApi(NotificationApi())
                addApi(EnvironmentApi())
                addApi(JavaClassApi())

                //Experimental Node.js Api implementation
                addApi(EventEmitterApi())

                // Legacy Apis
                addApi(UtilsApi())
                addApi(LegacyApi())
                addApi(BridgeApi())
                addApi(FileStreamApi())
                addApi(DataBaseApi())
                addApi(DeviceApi())

                // Api2 Apis
                addApi(AppApi())
                addApi(BroadcastApi())
            }

            Session.widgetManager.apply {
                //val name = "기본 위젯"
                addWidget(pContext, DummyWidgetSlim::class.java)
                addWidget(pContext, UptimeWidgetDefault::class.java)
                addWidget(pContext, UptimeWidgetSlim::class.java)
                addWidget(pContext, LogsWidget::class.java)
                addWidget(pContext, LogGenWidget::class.java)
            }

            registerProjectEvents(pContext) {
                category("message") {
                    add<ProjectOnMessageEvent>()
                    add<ProjectOnMessageDeleteEvent>()
                    add<LegacyEvent>()
                }
                category("notification") {
                    add<OnNotificationPostedEvent>()
                }
                category("project") {
                    add<ProjectOnStartCompileEvent>()
                }
            }

            emit(context.getString(R.string.step_projects))
            Session.projectLoader.loadProjects()

            setNetworkHandler(context)

            initState = InitState.Done
            emit(null)
            mInitMillis = System.currentTimeMillis()
        }

    internal fun shutdown() {
        try {
            Session.shutdown()
        } catch (e: Exception) {
            logger.wtf { "Failed to gracefully shutdown Session: ${e.localizedMessage}\ncause:\n${e.stackTrace}" }
        }
    }

    private fun registerProjectEvents(coreContext: PluginContext, builder: ProjectEventBuilder.() -> Unit) {
        val events = ProjectEventBuilder(coreContext).apply(builder).build()

        for ((id, event) in events) {
            ProjectEventManager.register(id, event)
        }
    }

    private fun setNetworkHandler(context: Context) {
        NetworkUtil.registerNetworkStatusListener(context)
        NetworkUtil.addOnNetworkStateChangedListener { state ->
            for (plugin in Session.pluginManager.plugins) {
                try {
                    for (listener in plugin.getListeners())
                        listener.onNetworkStateChanged(state)
                } catch (e: Error) {
                    logger.error { "Failed to call network event for plugin '${plugin.info.id}': $e" }
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
            logger.wtf { errMsg }

            val startupData = Session.json.encodeToString(mapOf("last_error" to errMsg))
            try {
                File(getStarLightDirectory(), "STARTUP.info").writeText(startupData)
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                shutdown()
                exitProcess(2)
            }
        }

    sealed class InitState {

        data object None: InitState()
        data object Running: InitState()
        data object Done: InitState()
    }
}