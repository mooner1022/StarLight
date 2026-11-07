package dev.mooner.starlight.plugincore

/**
 * Marks plugin code that runs only on hosts whose PluginCore is [version] or newer, as code reached after checking
 * `Info.PLUGINCORE_VERSION` is. The StarLight Gradle plugin (slplugin) works out a plugin's `api_version` from the
 * PluginCore API its code uses; what code marked with this uses, up to [version], is left out, so the plugin still loads
 * on older hosts. Lambdas and local classes inside the marked code count as part of it.
 *
 * It stays in class files only, so hosts whose PluginCore doesn't have it load the plugin all the same.
 *
 * ```kotlin
 * if (Info.PLUGINCORE_VERSION >= Version(1, 1, 0))
 *     createProject()
 *
 * @RequiresPluginCore("1.1.0")
 * private fun createProject() = Session.projectManager.newProject(events = events) {
 *     allowedEventIds = setOf("myplugin.*")   // added in 1.1.0
 * }
 * ```
 *
 * @param version The PluginCore version the code needs, such as `1.1.0`.
 * @since 1.1.0
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
)
annotation class RequiresPluginCore(val version: String)
