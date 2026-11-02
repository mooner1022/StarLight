package dev.mooner.starlight.agent.runtime

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.agent_channel_progress), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_ATTENTION, context.getString(R.string.agent_channel_attention), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_DONE, context.getString(R.string.agent_channel_done), NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }

    fun progress(context: Context, runs: Collection<LiveRun>, titleOf: (String) -> String): Notification {
        val first = runs.firstOrNull()
        val title = when {
            first == null  -> context.getString(R.string.agent_notification_working)
            runs.size == 1 -> titleOf(first.conversationId)
            else           -> context.getString(R.string.agent_notification_many, runs.size)
        }
        val builder = base(context, CHANNEL_PROGRESS)
            .setContentTitle(title)
            .setContentText(first?.let { phaseText(context, it) } ?: context.getString(R.string.agent_notification_working))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (first != null) {
            builder.setContentIntent(openChat(context, first.conversationId))
            builder.addAction(0, context.getString(R.string.agent_stop), action(context, AgentActionReceiver.ACTION_STOP, first.conversationId, null))
        }
        return builder.build()
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

    private fun notify(context: Context, id: Int, notification: Notification) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            return
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun approvalId(conversationId: String) = 0x5A2000 + (conversationId.hashCode() and 0xFFF)

    private fun doneId(conversationId: String) = 0x5A3000 + (conversationId.hashCode() and 0xFFF)
}
