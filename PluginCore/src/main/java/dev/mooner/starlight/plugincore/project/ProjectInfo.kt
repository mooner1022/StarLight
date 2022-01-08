package dev.mooner.starlight.plugincore.project

import kotlinx.serialization.Serializable

@Serializable
data class ProjectInfo(
    val name: String,
    val mainScript: String,
    val languageId: String,
    var isEnabled: Boolean,
    val createdMillis: Long = 0L,
    val listeners: MutableSet<String>,
    val pluginIds: MutableSet<String>,
    val packages: MutableSet<String>
)

class ProjectInfoBuilder {
    var name: String? = null
    var mainScript: String? = null
    var languageId: String? = null
    var isEnabled: Boolean = false
    var createdMillis: Long = 0L
    var listeners: MutableSet<String> = mutableSetOf()
    var pluginIds: MutableSet<String> = mutableSetOf()
    var packages: MutableSet<String> = mutableSetOf()

    private fun required(fieldName: String, value: Any?) {
        if (value == null) {
            throw IllegalArgumentException("Required field '$fieldName' is null")
        }
    }

    fun build(): ProjectInfo {

        required("name", name)
        required("mainScript", mainScript)
        required("languageId", languageId)

        return ProjectInfo(
            name = name!!,
            mainScript = mainScript!!,
            languageId = languageId!!,
            isEnabled = isEnabled,
            createdMillis = createdMillis,
            listeners = listeners,
            pluginIds = pluginIds,
            packages = packages
        )
    }
}