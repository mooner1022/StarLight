package dev.mooner.starlight.api.original

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.mooner.starlight.R
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.listener.event.NotificationClickEvent
import dev.mooner.starlight.listener.event.NotificationDismissEvent
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.utils.NotificationEventService
import dev.mooner.starlight.utils.createNotificationChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlin.properties.Delegates.notNull

private typealias Callback = (id: Int) -> Unit

class NotificationApi: Api<NotificationApi.Notification>() {

    @Suppress("unused")
    class Notification(
        private val id: Int,
        private val builder: NotificationBuilder
    ) {

        private val coroutineScope = CoroutineScope(Dispatchers.Default)

        private val clickAction: NotificationCompat.Action = let {
            val context = GlobalApplication.requireContext()
            val intent = Intent(context, NotificationEventService::class.java)
                .setAction("$INTENT_ACTION$$id")
            val pendingIntent = PendingIntent.getService(context, 0, intent, 0 or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(R.mipmap.ic_launcher, builder.onClickTitle, pendingIntent)
        }

        private var mState: Int = STATE_DEFAULT
        val state: Int get() = mState

        fun update(title: String?, message: String?) {
            require(state == STATE_CREATED) { "Notification with id: $id is not created or already canceled (state: $state)" }

            title?.let(builder::setTitle)
            message?.let(builder::setText)

            val context = GlobalApplication.requireContext()
            val builder = context.buildNotification()
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }

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

        private fun onDismiss(event: NotificationDismissEvent) {
            val notificationId = event.sbn.id
            if (notificationId != id) return
            purge()
        }

        private fun onClick(event: NotificationClickEvent) {
            if (event.notificationId != id) return
            builder.onClickListener?.invoke(id)
        }

        private fun Context.buildNotification(): NotificationCompat.Builder {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationCompat.Builder(this, CHANNEL_ID)
            } else {
                NotificationCompat.Builder(this)
            }.apply {
                setContentTitle(builder.mTitle)
                setSubText(builder.mText)
                setSmallIcon(R.mipmap.ic_launcher)
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

            Session.eventManager.apply {
                if (this@Notification.builder.onDismissListener != null)
                    on(coroutineScope, ::onDismiss)
                if (this@Notification.builder.onClickListener != null)
                    on(coroutineScope, ::onClick)
            }

            NotificationManagerCompat.from(context).notify(id, builder.build())
            mState = STATE_CREATED
        }

        companion object {
            const val STATE_DISMISSED = -1
            const val STATE_DEFAULT = 0
            const val STATE_CREATED = 1

            private const val CHANNEL_ID = "NotificationApiChannel"
            const val INTENT_ACTION = "NotificationApiAction"

            @JvmStatic
            fun create(id: Int): NotificationBuilder = NotificationBuilder(id)
        }
    }

    @Suppress("unused")
    class NotificationBuilder(
        private val id: Int
    ) {

        internal var mTitle: String by notNull()
        internal var mText: String by notNull()

        internal var lightArgb: Int? = null
        internal var lightOnMs: Int = 0
        internal var lightOffMs: Int = 0

        internal var onDismissListener: Callback? = null

        internal var onClickTitle: String? = null
        internal var onClickListener: Callback? = null

        fun setTitle(title: String): NotificationBuilder {
            mTitle = title
            return this
        }

        fun setText(text: String): NotificationBuilder {
            mText = text
            return this
        }

        fun lights(@ColorInt argb: Int, onMs: Int, offMs: Int): NotificationBuilder {
            lightArgb = argb
            lightOnMs = onMs
            lightOffMs = offMs
            return this
        }

        fun onDismiss(callback: Callback): NotificationBuilder {
            onDismissListener = callback
            return this
        }

        fun onClick(title: String, callback: Callback): NotificationBuilder {
            onClickTitle = title
            onClickListener = callback
            return this
        }

        fun build() =
            Notification(id, this)
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