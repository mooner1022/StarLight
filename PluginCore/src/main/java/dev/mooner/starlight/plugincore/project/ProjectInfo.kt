package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.serializers.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.properties.Delegates.notNull

@Serializable
data class ProjectInfo (
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val version: Int = 0,
    val name: String,
    val mainScript: String,
    val languageId: String,
    var isEnabled: Boolean,
    var isPinned: Boolean = false,
    val createdMillis: Long = System.currentTimeMillis(),
    val allowedEventIds: MutableSet<String> =
        hashSetOf("starlight.*"),
    val packages: MutableSet<String> =
        hashSetOf("com.kakao.talk")
)

class ProjectInfoBuilder {
    var name       : String by notNull()
    var mainScript : String by notNull()
    var languageId : String by notNull()

    fun build() = ProjectInfo(
        name = name,
        mainScript = mainScript,
        languageId = languageId,
        isEnabled = false,
        //allowedEventIds = allowedEventIds,
        //packages = packages
    )
}