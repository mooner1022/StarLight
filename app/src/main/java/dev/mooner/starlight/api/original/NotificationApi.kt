package dev.mooner.starlight.api.original

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.ColorInt
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.mooner.starlight.R
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.utils.NotificationEventService
import dev.mooner.starlight.utils.canPostNotification
import dev.mooner.starlight.utils.createNotificationChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

private typealias Callback = (id: Int) -> Unit
private val LOG = LoggerFactory.logger {  }

class NotificationApi: Api<NotificationApi.Notification>() {

    /**
     * Notifications a script posts on the device, shown as StarLight's.
     *
     * Start one with [create], set its title and text, and post it with `build()`; keep what `build()` returns to
     * change or remove the notification later:
     * ```js
     * const n = Notification.create(1001)
     *     .setTitle("Bot")
     *     .setText("Running")
     *     .build();
     * n.update(null, "Still running");
     * n.delete();
     * ```
     * Don't construct one with `new`: `build()` does. On Android 13 and later StarLight needs the notification
     * permission; without it nothing is shown.
     *
     * @see guide:app#permissions
     */
    @SuppressLint("MissingPermission")
    @Suppress("unused")
    class Notification private constructor(
        private val id: Int,
        private val builder: NotificationBuilder
    ) {

        private val coroutineScope = CoroutineScope(Dispatchers.Default)

        private val clickAction: NotificationCompat.Action = let {
            val context = GlobalApplication.requireContext()
            val intent = Intent(context, NotificationEventService::class.java)
                .setAction("$INTENT_ACTION$$id")
            val pendingIntent = PendingIntent.getService(context, 0, intent, 0 or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(R.drawable.ic_notification, builder.onClickTitle, pendingIntent)
        }

        private var mState: Int = STATE_DEFAULT

        /**
         * Where the notification stands: `STATE_CREATED` while posted, `STATE_DEFAULT` if it couldn't be posted, or
         * `STATE_DISMISSED` once deleted.
         *
         * The user swiping the notification away doesn't change it.
         */
        val state: Int get() = mState

        /**
         * Changes the posted notification's title, text or both.
         *
         * A notification the user swiped away is shown again.
         *
         * @param title The new title, or `null` to keep the current one.
         * @param message The new text, or `null` to keep the current one.
         * @throws IllegalArgumentException If the notification couldn't be posted or was deleted.
         */
        fun update(title: String?, message: String?) {
            require(state == STATE_CREATED) { "Notification with id: $id is not created or already canceled (state: $state)" }

            title?.let(builder::setTitle)
            message?.let(builder::setText)

            val context = GlobalApplication.requireContext()
            val builder = context.buildNotification()

            
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }

        /**
         * Removes the notification and calls its `onDismiss` callback.
         *
         * @throws IllegalArgumentException If the notification couldn't be posted or was already deleted.
         */
        fun delete() {
            require(state == STATE_CREATED) { "Notification with id: $id is not created or already canceled (state: $state)" }

            NotificationManagerCompat.from(GlobalApplication.requireContext()).cancel(id)
            purge()
        }

        private fun purge() {
            builder.onDismissListener?.invoke(id)
            coroutineScope.cancel()
            mState = STATE_DISMISSED
        }

        private fun onDismiss(event: Events.Notification.Dismiss) {
            val notificationId = event.sbn.id
            if (notificationId != id) return
            purge()
        }

        private fun onClick(event: Events.Notification.Click) {
            if (event.notificationId != id) return
            builder.onClickListener?.invoke(id)
        }

        private fun Context.buildNotification(): NotificationCompat.Builder {
            return NotificationCompat.Builder(this, CHANNEL_ID).apply {
                setContentTitle(builder.mTitle)
                setSubText(builder.mText)
                setSmallIcon(R.drawable.ic_notification)
                color = ContextCompat.getColor(this@buildNotification, R.color.main_bright)
                if (builder.lightArgb != null)
                    setLights(builder.lightArgb!!, builder.lightOnMs, builder.lightOffMs)
                addAction(clickAction)
                setShowWhen(false)
            }
        }

        init {
            val context = GlobalApplication.requireContext()
            context.createNotificationChannel(
                channelId = CHANNEL_ID,
                channelName = "NotificationApi 채널"
            )

            val builder = context.buildNotification()

            EventHandler.apply {
                if (this@Notification.builder.onDismissListener != null)
                    on(coroutineScope, ::onDismiss)
                if (this@Notification.builder.onClickListener != null)
                    on(coroutineScope, ::onClick)
            }

            if (!context.canPostNotification()) {
                LOG.error {
                    translate {
                        Locale.ENGLISH { "Unable to post or modify notification: Permission denied" }
                        Locale.KOREAN { "알림을 표시하거나 수정할 수 없음: 권한 거부" }
                    }
                }
            } else {
                NotificationManagerCompat.from(context).notify(id, builder.build())
                mState = STATE_CREATED
            }
        }

        companion object {
            /** The `state` of a notification removed with `delete()`. */
            const val STATE_DISMISSED = -1
            /** The `state` of a notification not posted, for lack of the notification permission. */
            const val STATE_DEFAULT = 0
            /** The `state` of a posted notification, until `delete()` removes it. */
            const val STATE_CREATED = 1

            /** Posts one; only [NotificationBuilder.build] does, so that scripts don't see a constructor. */
            internal fun post(id: Int, builder: NotificationBuilder): Notification =
                Notification(id, builder)

            private const val CHANNEL_ID = "NotificationApiChannel"

            /**
             * The intent action of the notifications' buttons, used inside StarLight.
             *
             * @suppress
             */
            const val INTENT_ACTION = "NotificationApiAction"

            /**
             * Starts a notification with the ID [id]: set its title and text on the result, then post it with `build`.
             *
             * @param id The notification's ID, chosen by the script. Posting a notification with the ID of one that is
             *   showing replaces it. StarLight's own notifications share these IDs: `1` is the one its background
             *   service shows, so pick another.
             */
            @JvmStatic
            fun create(id: Int): NotificationBuilder = NotificationBuilder(id)
        }
    }

    /**
     * A notification being set up: `Notification.create(id)` makes one, and `build()` posts it.
     *
     * Each setter returns the builder, so calls chain. The title and the text are required. Every notification shows
     * one button: without `onClick` it has no label and does nothing.
     */
    @Suppress("unused")
    class NotificationBuilder(
        private val id: Int
    ) {

        internal var mTitle: String? = null
        internal var mText: String? = null

        internal var lightArgb: Int? = null
        internal var lightOnMs: Int = 0
        internal var lightOffMs: Int = 0

        internal var onDismissListener: Callback? = null

        internal var onClickTitle: String? = null
        internal var onClickListener: Callback? = null

        /**
         * Sets the notification's title, which it must have.
         *
         * @param title The title.
         * @return This builder, for chaining.
         */
        fun setTitle(title: String): NotificationBuilder {
            mTitle = title
            return this
        }

        /**
         * Sets the notification's text, which it must have; Android shows it as the small subtext next to StarLight's
         * name at the top, not as the body.
         *
         * @param text The text.
         * @return This builder, for chaining.
         */
        fun setText(text: String): NotificationBuilder {
            mText = text
            return this
        }

        /**
         * Sets the color and the blinking of the device's notification light; it has no effect.
         *
         * On Android 8 and later, which StarLight requires, the light follows the settings of the notification's
         * channel instead.
         *
         * @param argb The color, as an ARGB integer.
         * @param onMs How long the light stays on in each blink, in milliseconds.
         * @param offMs How long it stays off between blinks, in milliseconds.
         * @return This builder, for chaining.
         */
        fun lights(@ColorInt argb: Int, onMs: Int, offMs: Int): NotificationBuilder {
            lightArgb = argb
            lightOnMs = onMs
            lightOffMs = offMs
            return this
        }

        /**
         * Sets a function that `delete()` calls; the user swiping the notification away doesn't call it.
         *
         * It runs on the thread that called `delete()`, before `delete()` returns.
         *
         * @param callback Called with the notification's ID.
         * @return This builder, for chaining.
         */
        fun onDismiss(callback: Callback): NotificationBuilder {
            onDismissListener = callback
            return this
        }

        /**
         * Adds a button labelled [title] to the notification, calling [callback] when it is tapped.
         *
         * Tapping the notification itself doesn't call it. It runs on a background thread of the app, not as one of the
         * project's events.
         *
         * @param title The button's label.
         * @param callback Called with the notification's ID.
         * @return This builder, for chaining.
         */
        fun onClick(title: String, callback: Callback): NotificationBuilder {
            onClickTitle = title
            onClickListener = callback
            return this
        }

        /**
         * Posts the notification; throws `IllegalStateException` if its title or text wasn't set.
         *
         * Without StarLight's notification permission, on Android 13 and later, nothing is shown: StarLight logs an
         * error in its own log, and the result's `state` is `STATE_DEFAULT`.
         *
         * @return The notification, to change with `update` or remove with `delete`.
         * @throws IllegalStateException If the title or the text wasn't set.
         */
        fun build(): Notification {
            check(mTitle != null) { "Set the notification's title with setTitle before build" }
            check(mText != null) { "Set the notification's text with setText before build" }
            return Notification.post(id, this)
        }
    }

    override val name: String = "Notification"

    override val objects: List<ApiObject> = listOf(
        function {
            name = "create"
            args = arrayOf(Int::class.java)
        }
    )

    override val instanceClass: Class<Notification> = Notification::class.java

    override val instanceType: InstanceType = InstanceType.CLASS

    override fun getInstance(project: Project): Any = Notification::class.java
}