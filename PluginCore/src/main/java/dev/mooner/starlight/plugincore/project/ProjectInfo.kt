package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.ApiLevel
import dev.mooner.starlight.plugincore.serializers.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.properties.Delegates.notNull

typealias ProjectID = UUID

@Serializable
data class ProjectInfo (
    @Serializable(with = UUIDSerializer::class)
    val id              : ProjectID = UUID.randomUUID(),
    val version         : Int = 0,
    val name            : String,
    val mainScript      : String,
    val languageId      : String,
    var isEnabled       : Boolean,
    var isPinned        : Boolean = false,
    val createdMillis   : Long = System.currentTimeMillis(),
    val allowedEventIds : MutableSet<String> =
        hashSetOf("starlight.*"),
    val packages        : MutableSet<String> =
        hashSetOf("com.kakao.talk"),
    /** IDs of the [ApiLevel]s the project chose; `null` for a project from before levels, which has every API. */
    var apiLevels       : Set<String>? = null,
)

class ProjectInfoBuilder {
    var name       : String by notNull()
    var mainScript : String by notNull()
    var languageId : String by notNull()
    /** The levels to start with; the default ones when not set. */
    var apiLevels  : Set<String>? = null

    fun build() = ProjectInfo(
        name = name,
        mainScript = mainScript,
        languageId = languageId,
        isEnabled = false,
        apiLevels = apiLevels ?: Session.apiLevelManager.defaultLevelIds,
        //allowedEventIds = allowedEventIds,
        //packages = packages
    )
}