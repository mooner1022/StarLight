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
import dev.mooner.starlight.agent.model.Conversation
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Plan
import dev.mooner.starlight.agent.model.Role
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

    /**
     * The ongoing notification of the runs going on. One run says what it is doing and on what, how many tools it has
     * used and how its last steps went; while its chat isn't open it asks to be a Live Update, which Android 16 QPR2
     * and later show as a chip in the status bar and a card on the lock screen (Samsung only with "Live notifications
     * for all apps" in the developer options); elsewhere it stays an ordinary notification.
     */
    fun progress(context: Context, hub: AgentHub): Notification {
        val runtime = hub.runtime
        val runs = runtime.live.value.values
        val run = runs.singleOrNull()
        val builder = base(context, CHANNEL_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (run == null) {
            val first = runs.firstOrNull()
            builder.setContentTitle(if (first == null) context.getString(R.string.agent_notification_working)
                    else context.getString(R.string.agent_notification_many, runs.size))
                .setContentText(first?.let { phaseText(context, it) } ?: context.getString(R.string.agent_notification_working))
                .setProgress(0, 0, true)
            first?.let { builder.setContentIntent(openChat(context, it.conversationId)) }
            if (runs.isNotEmpty())
                builder.setDeleteIntent(dismissed(context, runs.map { it.conversationId }))
            return builder.build()
        }

        val id = run.conversationId
        val conversation = hub.store.get(id)
        val steps = conversation?.let(::currentSteps).orEmpty()
        val approval = if (run.phase == LiveRun.Phase.APPROVAL) runtime.pendingApproval(id) else null
        // The title is the conversation's project when it has one; then the steps needn't repeat it.
        val withProject = conversation?.project == null
        val doing = when {
            approval != null -> context.getString(R.string.agent_notification_approval_short, approval.second)
            run.phase == LiveRun.Phase.RUNNING_TOOL -> {
                val target = steps.lastOrNull { it.result == null }?.let { ToolLabels.target(it.call.arguments, withProject) }
                listOfNotNull(phaseText(context, run), target?.takeIf(String::isNotBlank)).joinToString(" · ")
            }
            else -> phaseText(context, run)
        }
        // With a plan, its steps say more than the latest tool calls do.
        val plan = conversation?.let { Plan.shown(it.messages, running = true) }
        val lines = plan?.steps?.map { step -> "${PLAN_MARKS.getValue(step.status)} ${step.text}" }
            ?: steps.filter { it.result != null }.takeLast(RECENT_STEPS).map { step ->
                val mark = if (step.result!!.isError) "✗" else "✓"
                val target = ToolLabels.target(step.call.arguments, withProject)
                "$mark " + listOf(ToolLabels.label(context, step.call.name), target).filter(String::isNotBlank).joinToString(" · ")
            }
        builder.setContentTitle(conversation?.project ?: conversation?.title ?: context.getString(R.string.agent_notification_working))
            .setContentText(doing)
            .setStyle(NotificationCompat.BigTextStyle().bigText((listOf(doing) + lines).joinToString("\n")))
            .setContentIntent(openChat(context, id))
            .setDeleteIntent(dismissed(context, listOf(id)))
            .addAction(0, context.getString(R.string.agent_stop), action(context, AgentActionReceiver.ACTION_STOP, id, null))
        if (plan != null)
            builder.setSubText(context.getString(R.string.agent_plan_progress, plan.done, plan.steps.size))
        else if (steps.isNotEmpty())
            builder.setSubText(context.resources.getQuantityString(R.plurals.agent_tools_used, steps.size, steps.size))
        // Waiting on the user isn't progress; the approval notification, which alerts, has the buttons.
        when {
            approval != null -> builder.setShortCriticalText(context.getString(R.string.agent_chip_approval))
            plan != null     -> builder.setProgress(plan.steps.size, plan.done, false)
                .setShortCriticalText(context.getString(R.string.agent_plan_count, plan.done, plan.steps.size))
            else             -> builder.setProgress(0, 0, true)
        }
        // The time the run has taken, which a Live Update's chip shows when there's nothing more pressing.
        runtime.startedAt(id)?.let { builder.setWhen(it).setShowWhen(true).setUsesChronometer(true) }
        builder.setRequestPromotedOngoing(promotes(hub.settings.liveUpdates, runs, runtime.visibleConversation, runtime.dismissed.value))
        return builder.build()
    }

    private class Step(val call: Part.ToolCall, val result: Part.ToolResult?)

    /** The tool calls since the user last wrote, with their results so far. */
    private fun currentSteps(conversation: Conversation): List<Step> {
        val messages = conversation.messages
        // Messages the app sends on its own, such as asking for a reply the model left out, don't start a new run.
        val start = messages.indexOfLast { it.isUserTurn }
        val run = messages.drop(start + 1)
        val results = run.filter { it.role == Role.TOOL }
            .flatMap { it.parts.filterIsInstance<Part.ToolResult>() }
            .associateBy { it.callId }
        return run.filter { it.role == Role.ASSISTANT }
            .flatMap { it.toolCalls }
            .map { Step(it, results[it.id]) }
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
        val single = runs.singleOrNull()
        return listOf(
            runs.map { listOf(it.conversationId, it.phase, it.tool, it.retry) },
            single?.let { runtime.pendingApproval(it.conversationId) },
            // Each step adds a part, and with it the step count, the recent steps and the plan shown. Results of one turn
            // share a message, so messages alone don't count them.
            single?.let { hub.store.get(it.conversationId)?.messages?.sumOf { message -> message.parts.size } },
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

    /** The agent asked the user something; the first options can be picked from the notification. */
    fun questionAsked(hub: AgentHub, conversationId: String, question: AgentRuntime.Question) {
        val context = hub.context
        if (hub.runtime.visibleConversation == conversationId)
            return
        val first = question.questions.items.first()
        val builder = base(context, CHANNEL_ATTENTION)
            .setContentTitle(context.getString(R.string.agent_notification_question))
            .setContentText(first.question)
            .setSubText(hub.store.get(conversationId)?.title.orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(question.questions.items.joinToString("\n") { it.question }))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openChat(context, conversationId))
        // One pick answers one question; several questions, or several picks, need the chat.
        if (question.questions.items.size == 1 && !first.multiple)
            first.options.take(NOTIFICATION_OPTIONS).forEach { option ->
                builder.addAction(0, option.label, answerAction(context, conversationId, question.callId, option.label))
            }
        notify(context, questionId(conversationId), builder.build())
    }

    fun cancelQuestion(hub: AgentHub, conversationId: String) =
        NotificationManagerCompat.from(hub.context).cancel(questionId(conversationId))

    fun finished(hub: AgentHub, conversationId: String) {
        val context = hub.context
        if (!hub.settings.notifyOnFinish || hub.runtime.visibleConversation == conversationId)
            return
        val conversation = hub.store.get(conversationId) ?: return
        val reply = conversation.messages.lastOrNull { it.text.isNotBlank() && it.role == Role.ASSISTANT }
            ?.text?.plainPreview()
        notify(context, doneId(conversationId), base(context, CHANNEL_DONE)
            .setContentTitle(conversation.title)
            .setContentText(reply?.take(200) ?: context.getString(R.string.agent_notification_done))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reply?.take(1000) ?: context.getString(R.string.agent_notification_done)))
            .setAutoCancel(true)
            .setContentIntent(openChat(context, conversationId))
            .build())
    }

    /**
     * The run stopped at its step limit and waits to be told to go on. Said whatever the setting for finished runs
     * is, as a failure is: it isn't done, and nothing else would tell a user who is elsewhere.
     */
    fun paused(hub: AgentHub, conversationId: String, message: String) {
        val context = hub.context
        if (hub.runtime.visibleConversation == conversationId)
            return
        val title = hub.store.get(conversationId)?.title ?: return
        notify(context, doneId(conversationId), base(context, CHANNEL_DONE)
            .setContentTitle(context.getString(R.string.agent_notification_paused, title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
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
        LiveRun.Phase.QUESTION     -> context.getString(R.string.agent_phase_question)
        LiveRun.Phase.COMPACTING   -> context.getString(R.string.agent_phase_compacting)
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

    private fun answerAction(context: Context, conversationId: String, callId: String, answer: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (AgentActionReceiver.ACTION_ANSWER + conversationId + callId + answer).hashCode(),
            Intent(context, AgentActionReceiver::class.java)
                .setAction(AgentActionReceiver.ACTION_ANSWER)
                .putExtra(AgentActionReceiver.EXTRA_CONVERSATION, conversationId)
                .putExtra(AgentActionReceiver.EXTRA_CALL, callId)
                .putExtra(AgentActionReceiver.EXTRA_ANSWER, answer),
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

    /** How many finished steps the expanded notification lists. */
    private const val RECENT_STEPS = 3

    /** How the expanded notification marks the steps of a plan. */
    private val PLAN_MARKS = mapOf(Plan.Status.COMPLETED to "✓", Plan.Status.IN_PROGRESS to "▶", Plan.Status.PENDING to "○")

    private fun approvalId(conversationId: String) = 0x5A2000 + (conversationId.hashCode() and 0xFFF)

    private fun questionId(conversationId: String) = 0x5A5000 + (conversationId.hashCode() and 0xFFF)

    /** How many options a question's notification offers as buttons. */
    private const val NOTIFICATION_OPTIONS = 3

    private fun doneId(conversationId: String) = 0x5A3000 + (conversationId.hashCode() and 0xFFF)
}
