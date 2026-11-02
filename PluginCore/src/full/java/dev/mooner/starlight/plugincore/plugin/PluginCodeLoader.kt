package dev.mooner.starlight.plugincore.plugin

import android.content.Context
import dalvik.system.PathClassLoader
import java.io.File

/** Loads plugin dex. Kept out of the play flavor, which may not load code from outside the app. */
internal object PluginCodeLoader {

    const val isSupported = true

    fun createClassLoader(
        context       : Context,
        parent        : ClassLoader,
        dexFile       : File,
        nativeLibPath : String?,
        loader        : PluginLoader,
        info          : PluginInfo,
        file          : File,
    ): PluginHostClassLoader =
        PluginClassLoader(context, parent, dexFile, nativeLibPath, loader, info, file)

    /** Instantiates the plugin's own `custom_classloader` from its dex. */
    fun createCustomClassLoader(name: String, dexPath: String, parent: ClassLoader): ClassLoader {
        val tempClassLoader = PathClassLoader(dexPath, parent)
        val clazz = Class.forName(name, false, tempClassLoader)
        val primaryConstructor = clazz.constructors.find { cn ->
            cn.parameterTypes.size == 1 && ClassLoader::class.java.isAssignableFrom(cn.parameterTypes[0])
        } ?: clazz.getConstructor()

        return if (primaryConstructor.parameterTypes.isNotEmpty())
            primaryConstructor.newInstance(parent) as ClassLoader
        else
            primaryConstructor.newInstance() as ClassLoader
    }
}
