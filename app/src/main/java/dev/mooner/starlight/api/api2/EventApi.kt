/*
 * EventApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.api2

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project

/** MessengerBot R's `Event`: the names of the API2 events, for `bot.on(Event.MESSAGE, …)`. */
class EventApi: Api<EventApi.Event>() {

    override val name: String = "Event"

    override val objects: List<ApiObject> =
        getApiObjects<Event>()

    override val instanceClass: Class<Event> =
        Event::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any =
        Event()

    // Fields rather than getters, so the names stay upper case in both engines.
    class Event {
        @JvmField val MESSAGE = "message"
        @JvmField val COMMAND = "command"
        @JvmField val NOTIFICATION_POSTED = "notificationPosted"
        @JvmField val NOTIFICATION_REMOVED = "notificationRemoved"
        @JvmField val START_COMPILE = "startCompile"
        /** StarLight doesn't fire it. */
        @JvmField val TICK = "tick"
        /** StarLight doesn't fire it, as MessengerBot R doesn't. */
        @JvmField val BATTERY_LEVEL_CHANGED = "batteryLevelChanged"
        /** StarLight has no script activity, so these never fire; they're here for scripts that name them. */
        @JvmField val Activity = ActivityEvents()
    }

    class ActivityEvents {
        @JvmField val BACK_PRESSED = "activityBackPressed"
        @JvmField val CREATE = "activityCreate"
        @JvmField val DESTROY = "activityDestroy"
        @JvmField val PAUSE = "activityPause"
        @JvmField val RESTART = "activityRestart"
        @JvmField val RESUME = "activityResume"
        @JvmField val START = "activityStart"
        @JvmField val STOP = "activityStop"
    }
}
