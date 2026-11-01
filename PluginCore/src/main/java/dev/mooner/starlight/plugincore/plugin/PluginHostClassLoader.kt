package dev.mooner.starlight.plugincore.plugin

/**
 * The class loader a plugin is loaded with. Only the full flavor has one ([PluginClassLoader]);
 * builds without it load no plugin code, see [PluginCodeLoader].
 */
internal interface PluginHostClassLoader {

    val plugin: StarlightPlugin

    fun initialize(plugin: StarlightPlugin)

    fun findClass(name: String, checkGlobal: Boolean): Class<*>
}
