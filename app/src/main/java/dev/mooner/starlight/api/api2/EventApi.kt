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
    /**
     * MessengerBot R's `Event`: the names of the API2 events, for `bot.on(Event.MESSAGE, …)`; a global in API2
     * projects.
     *
     * Each constant is the event's name as a string, such as `"message"`. A listener is called only when the project
     * also receives the event's ID, such as `starlight.api2.message`. `TICK` and `Activity.*` are here for
     * scripts that name them, but never fire.
     *
     * @see guide:events#api2-listeners
     */
    class Event {
        /** `"message"`: a chat message arrived; listeners get an `Api2Message` (event ID `starlight.api2.message`). */
        @JvmField val MESSAGE = "message"
        /**
         * `"command"`: a message starting with the bot's command prefix arrived; listeners get an `Api2Command` (event
         * ID `starlight.api2.command`).
         *
         * Set the prefix first with `bot.setCommandPrefix(…)`.
         */
        @JvmField val COMMAND = "command"
        /**
         * `"notificationPosted"`: an app posted a notification; listeners get `(sbn, sessionManager)` (event ID
         * `starlight.api2.notification_posted`).
         *
         * `sessionManager` is a `SessionManager` made for this notification: its reply and mark-as-read actions, and
         * `bindSession` to make a room of it replyable. Fired only while the app setting **onNotificationPosted 이벤트 사용**
         * is on.
         */
        @JvmField val NOTIFICATION_POSTED = "notificationPosted"
        /**
         * `"notificationRemoved"`: a notification was removed; listeners get `(sbn, rankingMap, reason)` (event ID
         * `starlight.api2.notification_removed`).
         *
         * Fired only while the app setting **onNotificationPosted 이벤트 사용** is on.
         */
        @JvmField val NOTIFICATION_REMOVED = "notificationRemoved"
        /**
         * `"startCompile"`: the project is about to compile again; listeners get no arguments (event ID
         * `starlight.api2.start_compile`).
         */
        @JvmField val START_COMPILE = "startCompile"
        /** `"tick"`, an event StarLight never fires. */
        @JvmField val TICK = "tick"
        /**
         * The names of the activity events, such as `Event.Activity.CREATE`; StarLight has no script activity, so they
         * never fire.
         */
        @JvmField val Activity = ActivityEvents()
    }

    /**
     * The names of MessengerBot R's activity events, as `Event.Activity`.
     *
     * StarLight has no script activity, so these events never fire; the names are here for scripts that use them.
     */
    class ActivityEvents {
        /** `"activityBackPressed"`, an event StarLight never fires. */
        @JvmField val BACK_PRESSED = "activityBackPressed"
        /** `"activityCreate"`, an event StarLight never fires. */
        @JvmField val CREATE = "activityCreate"
        /** `"activityDestroy"`, an event StarLight never fires. */
        @JvmField val DESTROY = "activityDestroy"
        /** `"activityPause"`, an event StarLight never fires. */
        @JvmField val PAUSE = "activityPause"
        /** `"activityRestart"`, an event StarLight never fires. */
        @JvmField val RESTART = "activityRestart"
        /** `"activityResume"`, an event StarLight never fires. */
        @JvmField val RESUME = "activityResume"
        /** `"activityStart"`, an event StarLight never fires. */
        @JvmField val START = "activityStart"
        /** `"activityStop"`, an event StarLight never fires. */
        @JvmField val STOP = "activityStop"
    }
}
