/*
 * ApiLevels.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api

import android.content.Context
import dev.mooner.starlight.R
import dev.mooner.starlight.api.api2.AppApi
import dev.mooner.starlight.api.api2.BotManagerApi
import dev.mooner.starlight.api.api2.BroadcastApi
import dev.mooner.starlight.api.api2.DatabaseApi
import dev.mooner.starlight.api.api2.EventApi
import dev.mooner.starlight.api.api2.HomeWidgetApi
import dev.mooner.starlight.api.legacy.*
import dev.mooner.starlight.api.msgbot.AppDataApi
import dev.mooner.starlight.api.msgbot.GlobalLogApi
import dev.mooner.starlight.api.msgbot.HttpApi
import dev.mooner.starlight.api.msgbot.SecurityApi
import dev.mooner.starlight.api.node.EventEmitterApi
import dev.mooner.starlight.api.original.*
import dev.mooner.starlight.listener.event.*
import dev.mooner.starlight.media.api.MediaSharerApi
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiLevel
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import kotlin.reflect.KClass

private val LOG = LoggerFactory.logger {  }

/**
 * The API levels StarLight's projects choose from: its own, and those of MessengerBot R it is compatible with. Each
 * lists its objects and events here, whatever package they live in; objects the levels share, like `Log`, `Device` and
 * `FileStream`, are listed in each and a project gets them once.
 */
object ApiLevels {

    const val ORIGINAL = "starlight"
    const val LEGACY   = "msgbot.legacy"
    const val API2     = "msgbot.api2"

    private class Definition(
        val id: String,
        val name: Int,
        val group: Int,
        val description: Int,
        val apis: Set<KClass<out Api<*>>>,
        val events: Set<KClass<out ProjectEvent>>,
        val isDefault: Boolean = false,
        val preferredLanguageId: String? = null,
    )

    /** What MessengerBot R gives its legacy and API2 scripts alike. */
    private val MESSENGERBOT_COMMON: Set<KClass<out Api<*>>> = setOf(
        DeviceApi::class, FileStreamApi::class, BroadcastApi::class, AppDataApi::class, GlobalLogApi::class,
        HttpApi::class, SecurityApi::class,
    )

    private val definitions = listOf(
        Definition(
            id = ORIGINAL,
            name = R.string.api_level_original,
            group = R.string.api_level_group_starlight,
            description = R.string.api_level_original_description,
            apis = setOf(
                ProjectLoggerApi::class, EnvironmentApi::class, JavaClassApi::class, LanguagesApi::class,
                NotificationApi::class, PlatformApi::class, PluginsApi::class, ProjectsApi::class,
                HomeWidgetApi::class, EventEmitterApi::class, MediaSharerApi::class, StoreApi::class,
                ChatApi::class, ScheduleApi::class, FormatApi::class,
            ),
            events = setOf(
                ProjectOnMessageEvent::class, ProjectOnMessageDeleteEvent::class,
                OnNotificationPostedEvent::class, ProjectOnStartCompileEvent::class,
            ),
            isDefault = true,
            preferredLanguageId = NEONJS_LANGUAGE_ID,
        ),
        Definition(
            id = API2,
            name = R.string.api_level_api2,
            group = R.string.api_level_group_msgbot,
            description = R.string.api_level_api2_description,
            apis = setOf(
                ProjectLoggerApi::class, AppApi::class, BotManagerApi::class, EventApi::class, DatabaseApi::class,
            ) + MESSENGERBOT_COMMON,
            events = setOf(
                Api2MessageEvent::class, Api2CommandEvent::class, Api2NotificationPostedEvent::class,
                Api2NotificationRemovedEvent::class, Api2StartCompileEvent::class,
            ),
            preferredLanguageId = NEONJS_LANGUAGE_ID,
        ),
        Definition(
            id = LEGACY,
            name = R.string.api_level_legacy,
            group = R.string.api_level_group_msgbot,
            description = R.string.api_level_legacy_description,
            apis = setOf(
                ProjectLoggerApi::class, LegacyApi::class, UtilsApi::class, DataBaseApi::class, BridgeApi::class,
            ) + MESSENGERBOT_COMMON,
            events = setOf(
                LegacyEvent::class, OnNotificationPostedEvent::class, OnNotificationRemovedEvent::class,
                ProjectOnStartCompileEvent::class,
            ),
        ),
    )

    /** Registers the levels; after the APIs and project events, whose names and IDs they take. */
    fun register(context: Context) {
        val apis = Session.apiManager.getApis()
        for (definition in definitions) {
            val isNew = Session.apiLevelManager.register(ApiLevel(
                id = definition.id,
                name = context.getString(definition.name),
                group = context.getString(definition.group),
                description = context.getString(definition.description),
                apis = apis.filter { it::class in definition.apis }.mapTo(linkedSetOf()) { it.name },
                events = definition.events.mapNotNullTo(linkedSetOf(), ProjectEventManager::validateAndGetEventID),
                isDefault = definition.isDefault,
                preferredLanguageId = definition.preferredLanguageId,
            ))
            if (!isNew)
                LOG.warn { "API level ${definition.id} is registered already" }
        }
    }

    /**
     * Gives a project from before levels every level, which is what it had. The legacy `response` event used to need
     * the app-wide setting this replaces: where that was off, the project's allowed events now leave it out.
     */
    fun migrate(project: Project) {
        val info = project.info
        if (info.apiLevels != null)
            return
        info.apiLevels = Session.apiLevelManager.getLevels().mapTo(linkedSetOf()) { it.id }
        val legacyEventId = ProjectEventManager.validateAndGetEventID(LegacyEvent::class)
        if (!wasLegacyEventOn() && legacyEventId != null && project.isEventCallAllowed(legacyEventId))
            info.allowedEventIds += ProjectEventManager.EXCLUDE_PREFIX + legacyEventId
        project.saveInfo()
        LOG.info { "Gave project '${info.name}' the API levels ${info.apiLevels}" }
    }

    /**
     * Whether a project's top-level `onMessage` would run twice. Rhino finds event functions among a script's top-level
     * functions, where the MessengerBot template defines `onMessage` for `bot.addListener`, so with both the original and
     * API2 levels it is called as StarLight's event too. NeonJS calls only what a module exports.
     */
    fun hasListenerNameClash(levelIds: Set<String>?, languageId: String?): Boolean =
        languageId == RHINO_LANGUAGE_ID && levelIds != null && ORIGINAL in levelIds && API2 in levelIds

    private const val RHINO_LANGUAGE_ID = "JS_RHINO"
    private const val NEONJS_LANGUAGE_ID = "js_neon"

    private fun wasLegacyEventOn(): Boolean =
        GlobalConfig.category("notifications")
            .getSubCategory("event")
            ?.getBoolean("use_legacy_event", false)
            ?: false
}
