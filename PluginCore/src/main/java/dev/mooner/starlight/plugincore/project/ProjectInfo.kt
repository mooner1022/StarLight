package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.ApiLevel
import dev.mooner.starlight.plugincore.serializers.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.properties.Delegates.notNull

typealias ProjectID = UUID

/**
 * A project's info, as its `project.json` holds it: `project.info`.
 *
 * Change a project through the app or StarLight AI's tools: assigning to these doesn't save, and doesn't change how the
 * project runs.
 *
 * @see guide:projects#projectjson
 */
@Serializable
data class ProjectInfo (
    /**
     * The project's ID, a UUID; `String(info.id)` gives it as text, as `Projects.ofId` takes it.
     */
    @Serializable(with = UUIDSerializer::class)
    val id              : ProjectID = UUID.randomUUID(),
    /** @suppress */
    val version         : Int = 0,
    /**
     * The project's name, which `Projects.ofName` and the other APIs take.
     */
    val name            : String,
    /**
     * The script file compiled first, in the project's folder, such as `bot.js`.
     */
    val mainScript      : String,
    /**
     * The project's language: `js_neon` for NeonJS, `JS_RHINO` for Rhino, or a plugin's language's ID.
     */
    val languageId      : String,
    /**
     * Whether the project is on; scripts read it as `enabled`. To turn it on or off, call `project.setEnabled`.
     */
    var isEnabled       : Boolean,
    /**
     * Whether the user pinned the project to the top of the list; scripts read it as `pinned`.
     */
    var isPinned        : Boolean = false,
    /**
     * When the project was created, in milliseconds since 1970-01-01 UTC.
     */
    val createdMillis   : Long = System.currentTimeMillis(),
    /**
     * The allowed-event entries: event IDs, wildcards such as `starlight.*`, and exclusions starting with `!`.
     */
    val allowedEventIds : MutableSet<String> =
        hashSetOf("starlight.*"),
    /**
     * Messenger package names the project's info screen lists. StarLight doesn't choose messages by them.
     */
    val packages        : MutableSet<String> =
        hashSetOf("com.kakao.talk"),
    /**
     * The IDs of the API levels the project chose; `null` for a project from before levels, which has every API.
     */
    var apiLevels       : Set<String>? = null,
)

class ProjectInfoBuilder {
    var name       : String by notNull()
    var mainScript : String by notNull()
    var languageId : String by notNull()
    /** The levels to start with; the default ones when not set. */
    var apiLevels  : Set<String>? = null
    /** The allowed-event entries to start with; `starlight.*` when not set. The events given for starting code join them. */
    var allowedEventIds: Set<String>? = null

    fun build() = ProjectInfo(
        name = name,
        mainScript = mainScript,
        languageId = languageId,
        isEnabled = false,
        apiLevels = apiLevels ?: Session.apiLevelManager.defaultLevelIds,
        //packages = packages
    ).also { info ->
        allowedEventIds?.let { ids ->
            info.allowedEventIds.clear()
            info.allowedEventIds += ids
        }
    }
}