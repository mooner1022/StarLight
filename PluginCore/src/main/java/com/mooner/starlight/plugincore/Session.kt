package com.mooner.starlight.plugincore

import android.os.Build
import android.os.Environment
import com.mooner.starlight.plugincore.api.ApiManager
import com.mooner.starlight.plugincore.config.GlobalConfig
import com.mooner.starlight.plugincore.event.EventManager
import com.mooner.starlight.plugincore.language.LanguageManager
import com.mooner.starlight.plugincore.library.LibraryLoader
import com.mooner.starlight.plugincore.library.LibraryManager
import com.mooner.starlight.plugincore.library.LibraryManagerApi
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.plugin.PluginLoader
import com.mooner.starlight.plugincore.plugin.PluginManager
import com.mooner.starlight.plugincore.project.ProjectLoader
import com.mooner.starlight.plugincore.project.ProjectManager
import com.mooner.starlight.plugincore.utils.NetworkUtil
import com.mooner.starlight.plugincore.widget.WidgetManager
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.properties.Delegates

object Session {

    @JvmStatic
    private var isDuringInit = false

    @JvmStatic
    private var mIsInitComplete = false
    val isInitComplete get() = mIsInitComplete

    private val mJson: ThreadLocal<Json> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ThreadLocal.withInitial {
            Json {
                isLenient = true
                ignoreUnknownKeys = true
                prettyPrint = true
            }
        }
    } else {
        object : ThreadLocal<Json>() {
            override fun initialValue(): Json {
                return Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                    prettyPrint = true
                }
            }
        }
    }
    val json: Json
        get() = mJson.get()!!

    @Suppress("DEPRECATION")
    val globalConfig: GlobalConfig =
        GlobalConfig(File(Environment.getExternalStorageDirectory(), "StarLight/"))

    private var mLanguageManager: LanguageManager? = null
    val languageManager get() = mLanguageManager!!

    private var mProjectLoader: ProjectLoader? = null
    private var mProjectManager: ProjectManager? = null
    val projectManager get() = mProjectManager!!
    val projectLoader get() = mProjectLoader!!

    private var mPluginLoader: PluginLoader? = null
    private var mPluginManager: PluginManager? = null
    val pluginLoader get() = mPluginLoader!!
    val pluginManager get() = mPluginManager!!

    private var mWidgetManager: WidgetManager? = null
    val widgetManager get() = mWidgetManager!!

    private var mEventManager: EventManager? = null
    val eventManager get() = mEventManager!!

    private var mLibraryLoader: LibraryLoader? = null
    private var mLibraryManager: LibraryManager? = null
    private val libraryLoader: LibraryLoader? get() = mLibraryLoader
    val libraryManager: LibraryManager? get() = mLibraryManager

    private var mApiManager: ApiManager by Delegates.notNull()
    val apiManager get() = mApiManager

    const val isDebugging: Boolean = true

    private val onInitCompleteListeners: MutableList<() -> Unit> = arrayListOf()

    fun setOnInitCompleteListener(listener: () -> Unit) {
        if (isInitComplete) listener()
        else onInitCompleteListeners += listener
    }

    fun init(baseDir: File) {
        if (isInitComplete || isDuringInit) {
            Logger.w("Session", "Rejecting re-init of Session")
            return
        }
        isDuringInit = true

        val preStack = Thread.currentThread().stackTrace[2]
        if (!preStack.className.startsWith("com.mooner.starlight")) {
            throw IllegalAccessException("Illegal access to internal function init() from $preStack")
        }

        val projectDir = File(baseDir, "projects/")

        mLanguageManager = LanguageManager()
        mWidgetManager   = WidgetManager()
        mPluginLoader    = PluginLoader()
        mPluginManager   = PluginManager()
        mProjectManager  = ProjectManager(projectDir)
        mProjectLoader   = ProjectLoader(projectDir)
        mEventManager    = EventManager()
        mApiManager      = ApiManager()

        if (globalConfig.getCategory("beta_features").getBoolean("load_external_dex_libs", false)) {
            mLibraryLoader = LibraryLoader()
            mLibraryManager = LibraryManager(libraryLoader!!.loadLibraries(baseDir).toMutableSet())
            apiManager.addApi(LibraryManagerApi())
        }

        for (listener in onInitCompleteListeners) listener()
        isDuringInit = false
        mIsInitComplete = true
    }

    fun shutdown() {
        val preStack = Thread.currentThread().stackTrace[2]
        if (!preStack.className.startsWith("com.mooner.starlight")) {
            throw IllegalAccessException("Illegal access to internal function shutdown()")
        }

        //globalConfig.push()

        NetworkUtil.purge()
        apiManager.purge()

        mLanguageManager?.purge()
        mWidgetManager?.purge()
        mPluginLoader?.purge()
        mPluginManager?.purge()
        mProjectManager?.purge()
        mEventManager?.purge()
        mLanguageManager = null
        mWidgetManager = null
        mPluginManager = null
        mPluginLoader = null
        mProjectManager = null
        mEventManager = null

        mJson.remove()
    }
}