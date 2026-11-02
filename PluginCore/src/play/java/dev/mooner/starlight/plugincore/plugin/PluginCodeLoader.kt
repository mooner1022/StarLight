package dev.mooner.starlight.plugincore.plugin

import android.content.Context
import java.io.File

/**
 * Google Play builds may not load code from outside the app (Device and Network Abuse policy), so
 * they load no plugins. The app doesn't call [PluginLoader.loadPlugins] in this flavor.
 */
internal object PluginCodeLoader {

    const val isSupported = false

    @Suppress("UNUSED_PARAMETER")
    fun createClassLoader(
        context       : Context,
        parent        : ClassLoader,
        dexFile       : File,
        nativeLibPath : String?,
        loader        : PluginLoader,
        info          : PluginInfo,
        file          : File,
    ): PluginHostClassLoader =
        throw UnsupportedOperationException(UNSUPPORTED)

    @Suppress("UNUSED_PARAMETER")
    fun createCustomClassLoader(name: String, dexPath: String, parent: ClassLoader): ClassLoader =
        throw UnsupportedOperationException(UNSUPPORTED)

    private const val UNSUPPORTED = "This build doesn't load plugin code"
}
