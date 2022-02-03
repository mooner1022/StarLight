package dev.mooner.starlight.plugincore.project

import kotlinx.serialization.Serializable
import kotlin.properties.Delegates

@Serializable
data class ProjectInfo (
    val version: Int = 0,
    val name: String,
    val mainScript: String,
    val languageId: String,
    var isEnabled: Boolean,
    var trust: Boolean = true,
    val createdMillis: Long = System.currentTimeMillis(),
    val allowedEventIds: MutableSet<String> = hashSetOf("default"),
    val packages: MutableSet<String>
)

class ProjectInfoBuilder {
    var name: String by Delegates.notNull()
    var mainScript: String by Delegates.notNull()
    var languageId: String by Delegates.notNull()
    var trust: Boolean = true
    var allowedEventIds: MutableSet<String> = hashSetOf("default")
    var packages: MutableSet<String> = hashSetOf("com.kakao.talk")

    fun build() = ProjectInfo(
        name = name,
        mainScript = mainScript,
        languageId = languageId,
        isEnabled = false,
        trust = trust,
        allowedEventIds = allowedEventIds,
        packages = packages
    )
}