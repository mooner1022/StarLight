package dev.mooner.starlight.api

import dev.mooner.starlight.api.api2.AppApi
import dev.mooner.starlight.api.api2.BotManagerApi
import dev.mooner.starlight.api.api2.BroadcastApi
import dev.mooner.starlight.api.api2.EventApi
import dev.mooner.starlight.api.api2.HomeWidgetApi
import dev.mooner.starlight.api.legacy.BridgeApi
import dev.mooner.starlight.api.legacy.DataBaseApi
import dev.mooner.starlight.api.legacy.DeviceApi
import dev.mooner.starlight.api.legacy.FileStreamApi
import dev.mooner.starlight.api.legacy.LegacyApi
import dev.mooner.starlight.api.legacy.UtilsApi
import dev.mooner.starlight.api.node.EventEmitterApi
import dev.mooner.starlight.api.original.EnvironmentApi
import dev.mooner.starlight.api.original.JavaClassApi
import dev.mooner.starlight.api.original.LanguagesApi
import dev.mooner.starlight.api.original.NotificationApi
import dev.mooner.starlight.api.original.PlatformApi
import dev.mooner.starlight.api.original.PluginsApi
import dev.mooner.starlight.api.original.ProjectLoggerApi
import dev.mooner.starlight.api.original.ProjectsApi
import dev.mooner.starlight.listener.event.Api2CommandEvent
import dev.mooner.starlight.listener.event.Api2MessageEvent
import dev.mooner.starlight.listener.event.Api2NotificationPostedEvent
import dev.mooner.starlight.listener.event.Api2NotificationRemovedEvent
import dev.mooner.starlight.listener.event.Api2StartCompileEvent
import dev.mooner.starlight.listener.event.LegacyEvent
import dev.mooner.starlight.listener.event.OnNotificationPostedEvent
import dev.mooner.starlight.listener.event.ProjectOnMessageDeleteEvent
import dev.mooner.starlight.listener.event.ProjectOnMessageEvent
import dev.mooner.starlight.listener.event.ProjectOnStartCompileEvent
import dev.mooner.starlight.media.api.MediaSharerApi
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.project.event.ProjectEventClass

/**
 * The script APIs and project events the app itself registers. The agent docs' coverage test checks these same lists,
 * so an API or event added here without docs fails it.
 */
object CoreApis {

    /** Registered before plugins load, so that an installed Media Sharer plugin's API is skipped as a duplicate. */
    fun earlyApis(): List<Api<*>> = listOf(
        MediaSharerApi(),
    )

    /** Registered after plugins load. */
    fun apis(): List<Api<*>> = listOf(
        // StarLight original
        LanguagesApi(),
        ProjectLoggerApi(),
        ProjectsApi(),
        PluginsApi(),
        NotificationApi(),
        EnvironmentApi(),
        PlatformApi(),
        JavaClassApi(),
        // Experimental Node.js API
        EventEmitterApi(),
        // MessengerBot legacy
        UtilsApi(),
        LegacyApi(),
        BridgeApi(),
        FileStreamApi(),
        DataBaseApi(),
        DeviceApi(),
        // MessengerBot API2
        AppApi(),
        BroadcastApi(),
        BotManagerApi(),
        EventApi(),
        HomeWidgetApi(),
    )

    /** By category; each is registered as `starlight.<category>.<event id>`. */
    val events: Map<String, List<ProjectEventClass>> = linkedMapOf(
        "message" to listOf(ProjectOnMessageEvent::class, ProjectOnMessageDeleteEvent::class, LegacyEvent::class),
        "notification" to listOf(OnNotificationPostedEvent::class),
        "project" to listOf(ProjectOnStartCompileEvent::class),
        "api2" to listOf(
            Api2MessageEvent::class,
            Api2CommandEvent::class,
            Api2NotificationPostedEvent::class,
            Api2NotificationRemovedEvent::class,
            Api2StartCompileEvent::class,
        ),
    )
}
