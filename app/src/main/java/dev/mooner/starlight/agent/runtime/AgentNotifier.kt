package dev.mooner.starlight.agent.runtime

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.model.plainPreview
import dev.mooner.starlight.ui.agent.AgentChatActivity

/** The agent's notifications: the ongoing run, tool calls waiting for the user, and finished runs. */
internal object AgentNotifier {

    const val CHANNEL_PROGRESS = "agent_progress"
    const val CHANNEL_ATTENTION = "agent_attention"
    const val CHANNEL_DONE = "agent_done"

    const val PROGRESS_ID = 0x5A1
    const val TEST_ID = 0x5A5
    const val TEST_MILLIS = 20_000L

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.agent_channel_progress), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_ATTENTION, context.getString(R.string.agent_channel_attention), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_DONE, context.getString(R.string.agent_channel_done), NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }

    /**
     * The ongoing notification of the runs going on. One run whose chat isn't open asks to be a Live Update, which
     * Android 16 QPR2 and later show as a chip in the status bar and a card on the lock screen; elsewhere it stays an
     * ordinary notification. A run waiting for the user says so in the chip and offers to allow or deny right there.
     */
    fun progress(context: Context, hub: AgentHub): Notification {
        val runtime = hub.runtime
        val runs = runtime.live.value.values
        val first = runs.firstOrNull()
        val title = when {
            first == null  -> context.getString(R.string.agent_notification_working)
            runs.size == 1 -> hub.store.get(first.conversationId)?.title.orEmpty()
            else           -> context.getString(R.string.agent_notification_many, runs.size)
        }
        val builder = base(context, CHANNEL_PROGRESS)
            .setContentTitle(title)
            .setContentText(first?.let { phaseText(context, it) } ?: context.getString(R.string.agent_notification_working))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // As in Google's Live Updates sample; below Android 16 this is the usual indeterminate bar.
            .setStyle(NotificationCompat.ProgressStyle().setProgressIndeterminate(true))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (first == null)
            return builder.build()
        builder.setContentIntent(openChat(context, first.conversationId))
            .setDeleteIntent(dismissed(context, runs.map { it.conversationId }))
        val single = runs.size == 1
        val approval = if (single && first.phase == LiveRun.Phase.APPROVAL) runtime.pendingApproval(first.conversationId) else null
        if (approval != null) {
            val (callId, description) = approval
            builder.setContentText(description)
                .setShortCriticalText(context.getString(R.string.agent_chip_approval))
                .addAction(0, context.getString(R.string.agent_allow), action(context, AgentActionReceiver.ACTION_ALLOW, first.conversationId, callId))
                .addAction(0, context.getString(R.string.agent_deny), action(context, AgentActionReceiver.ACTION_DENY, first.conversationId, callId))
        } else {
            builder.addAction(0, context.getString(R.string.agent_stop), action(context, AgentActionReceiver.ACTION_STOP, first.conversationId, null))
        }
        // The time the run has taken, which a Live Update's chip shows when there's nothing more pressing.
        if (single)
            runtime.startedAt(first.conversationId)?.let { builder.setWhen(it).setShowWhen(true).setUsesChronometer(true) }
        builder.setRequestPromotedOngoing(
            promotes(hub.settings.liveUpdates, runs, runtime.visibleConversation, runtime.dismissed.value))
        return builder.build()
    }

    /** A run's progress notification as it looks away from its chat, for the settings to test Live Updates with. */
    fun liveUpdateTest(context: Context): Notification =
        base(context, CHANNEL_PROGRESS)
            .setContentTitle(context.getString(R.string.agent_live_updates_test_title))
            .setContentText(context.getString(R.string.agent_phase_thinking))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.ProgressStyle().setProgressIndeterminate(true))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setTimeoutAfter(TEST_MILLIS)
            .setRequestPromotedOngoing(true)
            .build()

    /**
     * What the system made of a posted notification that asked to be a Live Update, and one line saying why: the API
     * level, whether the app may post Live Updates, whether the notification qualifies, its channel's importance and
     * whether it was promoted. Each is a different place where promotion can fail.
     */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    fun promotionReport(context: Context, posted: Notification): Pair<Boolean, String> {
        val manager = context.getSystemService(NotificationManager::class.java)
        val promoted = posted.flags and Notification.FLAG_PROMOTED_ONGOING != 0
        val full = Build.VERSION.SDK_INT_FULL
        fun mark(value: Boolean?) = when (value) {
            true  -> "✓"
            false -> "✗"
            null  -> "?"
        }
        val detail = listOf(
            "API ${full / 100_000}.${full % 100_000}",
            "allowed ${mark(runCatching { manager?.canPostPromotedNotifications() }.getOrNull())}",
            "qualifies ${mark(runCatching { posted.hasPromotableCharacteristics() }.getOrNull())}",
            "channel ${manager?.getNotificationChannel(posted.channelId)?.importance ?: "?"}",
            "promoted ${mark(promoted)}",
        ).joinToString(" · ")
        return promoted to detail
    }

    /** Whether the progress notification asks to be a Live Update: one run, its chat not open, not swiped away. */
    internal fun promotes(enabled: Boolean, runs: Collection<LiveRun>, visible: String?, dismissed: Set<String>): Boolean {
        val run = runs.singleOrNull() ?: return false
        return enabled && run.conversationId != visible && run.conversationId !in dismissed
    }

    /** What the progress notification shows; it is posted again only when this changes, not on every streamed word. */
    fun progressKey(hub: AgentHub): List<Any?> {
        val runtime = hub.runtime
        val runs = runtime.live.value.values
        return listOf(
            runs.map { listOf(it.conversationId, it.phase, it.tool, it.retry) },
            runs.singleOrNull()?.let { runtime.pendingApproval(it.conversationId) },
            promotes(hub.settings.liveUpdates, runs, runtime.visibleConversation, runtime.dismissed.value),
        )
    }

    fun approvalNeeded(hub: AgentHub, conversationId: String, callId: String, description: String) {
        val context = hub.context
        if (hub.runtime.visibleConversation == conversationId)
            return
        val title = hub.store.get(conversationId)?.title.orEmpty()
        val notification = base(context, CHANNEL_ATTENTION)
            .setContentTitle(context.getString(R.string.agent_notification_approval))
            .setContentText(description)
            .setSubText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openChat(context, conversationId))
            .addAction(0, context.getString(R.string.agent_allow), action(context, AgentActionReceiver.ACTION_ALLOW, conversationId, callId))
            .addAction(0, context.getString(R.string.agent_deny), action(context, AgentActionReceiver.ACTION_DENY, conversationId, callId))
            .build()
        notify(context, approvalId(conversationId), notification)
    }

    fun cancelApproval(hub: AgentHub, conversationId: String) =
        NotificationManagerCompat.from(hub.context).cancel(approvalId(conversationId))

    fun finished(hub: AgentHub, conversationId: String) {
        val context = hub.context
        if (!hub.settings.notifyOnFinish || hub.runtime.visibleConversation == conversationId)
            return
        val conversation = hub.store.get(conversationId) ?: return
        val reply = conversation.messages.lastOrNull { it.text.isNotBlank() && it.role == dev.mooner.starlight.agent.model.Role.ASSISTANT }
            ?.text?.plainPreview()
        notify(context, doneId(conversationId), base(context, CHANNEL_DONE)
            .setContentTitle(conversation.title)
            .setContentText(reply?.take(200) ?: context.getString(R.string.agent_notification_done))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reply?.take(1000) ?: context.getString(R.string.agent_notification_done)))
            .setAutoCancel(true)
            .setContentIntent(openChat(context, conversationId))
            .build())
    }

    fun failed(hub: AgentHub, conversationId: String, message: String) {
        val context = hub.context
        if (hub.runtime.visibleConversation == conversationId)
            return
        val title = hub.store.get(conversationId)?.title ?: return
        notify(context, doneId(conversationId), base(context, CHANNEL_DONE)
            .setContentTitle(context.getString(R.string.agent_notification_failed, title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openChat(context, conversationId))
            .build())
    }

    fun phaseText(context: Context, run: LiveRun): String = when (run.phase) {
        LiveRun.Phase.WAITING      -> context.getString(R.string.agent_phase_waiting)
        LiveRun.Phase.THINKING     -> context.getString(R.string.agent_phase_thinking)
        LiveRun.Phase.WRITING      -> context.getString(R.string.agent_phase_writing)
        LiveRun.Phase.CALLING_TOOL,
        LiveRun.Phase.RUNNING_TOOL -> context.getString(R.string.agent_phase_tool, ToolLabels.label(context, run.tool))
        LiveRun.Phase.APPROVAL     -> context.getString(R.string.agent_phase_approval)
        LiveRun.Phase.RETRYING     -> context.getString(R.string.agent_phase_retrying, run.retry)
    }

    private fun base(context: Context, channel: String): NotificationCompat.Builder {
        ensureChannels(context)
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.main_bright))
    }

    private fun openChat(context: Context, conversationId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            AgentChatActivity.intent(context, conversationId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun action(context: Context, action: String, conversationId: String, callId: String?): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (action + conversationId + callId).hashCode(),
            Intent(context, AgentActionReceiver::class.java)
                .setAction(action)
                .putExtra(AgentActionReceiver.EXTRA_CONVERSATION, conversationId)
                .putExtra(AgentActionReceiver.EXTRA_CALL, callId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun dismissed(context: Context, conversationIds: List<String>): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0x5A4,
            Intent(context, AgentActionReceiver::class.java)
                .setAction(AgentActionReceiver.ACTION_DISMISSED)
                .putExtra(AgentActionReceiver.EXTRA_CONVERSATIONS, conversationIds.toTypedArray()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notify(context: Context, id: Int, notification: Notification) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            return
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun approvalId(conversationId: String) = 0x5A2000 + (conversationId.hashCode() and 0xFFF)

    private fun doneId(conversationId: String) = 0x5A3000 + (conversationId.hashCode() and 0xFFF)
}
