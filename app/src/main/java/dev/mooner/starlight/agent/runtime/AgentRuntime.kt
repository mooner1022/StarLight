package dev.mooner.starlight.agent.runtime

import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.ApprovalPolicy
import dev.mooner.starlight.agent.auth.SignInRequiredException
import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.model.Approval
import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Conversation
import dev.mooner.starlight.agent.model.NoticeKind
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.model.RunState
import dev.mooner.starlight.agent.net.HttpStatusException
import dev.mooner.starlight.agent.provider.LlmRequest
import dev.mooner.starlight.agent.provider.LlmResult
import dev.mooner.starlight.agent.provider.RequestContent
import dev.mooner.starlight.agent.provider.StopReason
import dev.mooner.starlight.agent.provider.StreamErrorException
import dev.mooner.starlight.agent.provider.StreamSink
import dev.mooner.starlight.agent.provider.string
import dev.mooner.starlight.agent.store.Rewind
import dev.mooner.starlight.agent.tools.ToolContext
import dev.mooner.starlight.agent.tools.ToolException
import dev.mooner.starlight.agent.tools.ToolOutput
import dev.mooner.starlight.agent.tools.ToolRisk
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val LOG = LoggerFactory.logger("Agent")

/** What a running conversation is doing right now, for the chat screen and the notification. */
data class LiveRun(
    val conversationId: String,
    val phase: Phase,
    val text: String = "",
    val thinking: String = "",
    /** The tool the model is writing a call for, or the one running. */
    val tool: String? = null,
    val retry: Int = 0,
    val error: String? = null,
) {
    enum class Phase {
        WAITING,
        THINKING,
        WRITING,
        CALLING_TOOL,
        RUNNING_TOOL,
        APPROVAL,
        RETRYING,
    }
}

/**
 * Runs conversations: sends them to the model, runs the tools it calls, and repeats until it answers. Runs live in
 * the process, not in a screen, so they go on when the app is closed; [AgentService] keeps the process in the
 * foreground meanwhile. Every finished step is saved, so a run the system kills can be continued.
 */
class AgentRuntime internal constructor(private val hub: AgentHub) {

    /** A failure that slips past a run's own handling is logged; it must not take the bots down with the app. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
        LOG.error { "Agent run crashed: $e" }
    })
    private val jobs = ConcurrentHashMap<String, Job>()
    private val approvals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    /** Tools the user allowed for the rest of a conversation, by conversation. */
    private val alwaysAllowed = ConcurrentHashMap<String, MutableSet<String>>()
    private val rewinder = Rewinder(hub)

    private val mLive = MutableStateFlow<Map<String, LiveRun>>(emptyMap())
    val live: StateFlow<Map<String, LiveRun>> = mLive.asStateFlow()

    private val mVisible = MutableStateFlow<String?>(null)
    /** The conversation shown on screen, which needs no notification when it finishes. */
    val visible: StateFlow<String?> = mVisible.asStateFlow()
    val visibleConversation: String?
        get() = mVisible.value

    /** Approvals waiting for the user, by conversation: the call and what it would do. */
    private val pendingApprovals = ConcurrentHashMap<String, Pair<String, String>>()
    /** The diff a call waiting for approval would make, by [approvalKey], for the approval card. */
    private val approvalPreviews = ConcurrentHashMap<String, String>()
    /** When each run going on started. */
    private val startedAt = ConcurrentHashMap<String, Long>()
    private val mDismissed = MutableStateFlow<Set<String>>(emptySet())
    /** Runs whose progress notification the user swiped away; they aren't shown as a Live Update again. */
    val dismissed: StateFlow<Set<String>> = mDismissed.asStateFlow()

    fun chatShown(conversationId: String) {
        mVisible.value = conversationId
        AgentNotifier.cancelApproval(hub, conversationId)
    }

    /** The chat went away; a question it was showing moves to a notification. */
    fun chatHidden(conversationId: String) {
        mVisible.compareAndSet(conversationId, null)
        pendingApprovals[conversationId]?.let { (callId, description) ->
            AgentNotifier.approvalNeeded(hub, conversationId, callId, description)
        }
    }

    /** The call [conversationId] waits on the user for, and what it would do. */
    fun pendingApproval(conversationId: String): Pair<String, String>? =
        pendingApprovals[conversationId]

    /** What the call [callId] waiting for approval would change, as a diff, if its tool can tell. */
    fun approvalPreview(conversationId: String, callId: String): String? =
        approvalPreviews[approvalKey(conversationId, callId)]

    fun startedAt(conversationId: String): Long? =
        startedAt[conversationId]

    /** The user swiped the progress notification away while these runs went on. */
    fun progressDismissed(conversationIds: Collection<String>) {
        mDismissed.update { it + conversationIds }
    }

    /** True from the start of a run until it has fully ended, stopping included: a stopped tool may still be finishing. */
    fun isRunning(conversationId: String): Boolean =
        jobs[conversationId]?.isCompleted == false

    fun newConversationId(): String =
        "c" + UUID.randomUUID().toString().replace("-", "").take(16)

    /**
     * Adds the user's message to [conversationId], creating the conversation if it doesn't exist, and runs it.
     * [project] is the project the conversation is about: a new conversation starts with its context, and a
     * different one later tells the model about the switch in this message, since history is never rewritten.
     */
    fun send(
        conversationId: String,
        text: String,
        attachments: List<Part.Attachment>,
        project: String? = null,
        approval: ApprovalPolicy? = null,
        /** The message replaces one the user rewrote. */
        edited: Boolean = false,
    ) {
        check(!isRunning(conversationId)) { "The conversation is still running" }
        val now = System.currentTimeMillis()
        val existing = hub.store.get(conversationId)
        val parts = buildList {
            if (existing == null || existing.messages.none { it.role == Role.USER })
                addAll(contextParts(project))
            else if (project != existing.project)
                add(switchPart(project))
            // Told along with the message, not shown: what changed since the agent last looked.
            existing?.pendingNote?.let { add(Part.Context(Part.Context.INSTRUCTIONS, it)) }
            addAll(attachments)
            if (text.isNotBlank())
                add(Part.Text(text.trim()))
        }
        val message = ChatMessage(newId("m"), Role.USER, parts, now, edited = edited)
        val conversation = existing ?: Conversation(
            id = conversationId,
            title = titleOf(text, attachments),
            createdAt = now,
            updatedAt = now,
        )
        hub.store.save(conversation.copy(
            messages = conversation.messages + message,
            updatedAt = now,
            error = null,
            project = project,
            approval = approval?.id ?: conversation.approval,
            pendingNote = null,
        ))
        start(conversationId)
    }

    /**
     * Replaces the user's message [messageId], and everything after it, with a new message and runs the conversation
     * from there. Only the conversation goes back; what the agent changed in projects meanwhile stays, and the agent
     * is told so. [rewindAndResend] puts that back first.
     */
    fun resend(
        conversationId: String,
        messageId: String,
        text: String,
        attachments: List<Part.Attachment>,
        project: String? = null,
        approval: ApprovalPolicy? = null,
    ) {
        check(!isRunning(conversationId)) { "The conversation is still running" }
        val conversation = hub.store.get(conversationId) ?: return send(conversationId, text, attachments, project, approval)
        val index = conversation.messages.indexOfFirst { it.id == messageId }
        require(index >= 0 && conversation.messages[index].role == Role.USER) { "No user message $messageId" }
        val firstMessage = conversation.messages.take(index).none { it.role == Role.USER }
        val stays = rewindable(conversation, messageId)
        cut(
            conversation, index,
            title = if (firstMessage) titleOf(text, attachments) else conversation.title,
            keep = attachments,
            note = stays.takeIf { it.isNotEmpty() }?.let {
                "The user rewrote an earlier message, which took your later turns out of this conversation, but what " +
                    "you changed in them stays: ${listed(it)}."
            },
        )
        send(conversationId, text, attachments, project, approval, edited = true)
    }

    /** Like [resend], after putting back what the agent changed from [messageId] on. */
    suspend fun rewindAndResend(
        conversationId: String,
        messageId: String,
        text: String,
        attachments: List<Part.Attachment>,
        project: String? = null,
        approval: ApprovalPolicy? = null,
    ): Rewind.Result {
        check(!isRunning(conversationId)) { "The conversation is still running" }
        val conversation = hub.store.get(conversationId) ?: return NOTHING_REWOUND
        val result = rewinder.rewind(conversationId, turnsFrom(conversation, messageId))
        withContext(Dispatchers.Main) { resend(conversationId, messageId, text, attachments, project, approval) }
        return result
    }

    /** What the agent changed from the user's message [messageId] on, which rewinding to before it puts back. */
    fun rewindable(conversationId: String, messageId: String): List<String> =
        hub.store.get(conversationId)?.let { rewindable(it, messageId) }.orEmpty()

    private fun rewindable(conversation: Conversation, messageId: String): List<String> =
        hub.checkpoints.changes(conversation.id, turnsFrom(conversation, messageId)).map { Rewind.describe(it.entry) }

    /**
     * Puts back what the agent changed in projects from the user's message [messageId] on. With [conversationToo] the
     * conversation goes back to before that message as well; otherwise it stays, and the agent is told what changed.
     */
    suspend fun rewind(conversationId: String, messageId: String, conversationToo: Boolean): Rewind.Result {
        check(!isRunning(conversationId)) { "The conversation is still running" }
        val conversation = hub.store.get(conversationId) ?: return NOTHING_REWOUND
        val index = conversation.messages.indexOfFirst { it.id == messageId }
        require(index >= 0) { "No message $messageId" }
        val result = rewinder.rewind(conversationId, turnsFrom(conversation, messageId))
        val current = hub.store.get(conversationId) ?: return result
        if (conversationToo) {
            // The message goes back to the composer, attachments and all.
            cut(current, index, title = current.title, keep = current.messages[index].attachments, note = null)
        } else if (result.restored.isNotEmpty()) {
            val asked = current.messages[index].text.lineSequence().firstOrNull().orEmpty().take(NOTE_QUOTE_CHARS)
            hub.store.save(current.copy(pendingNote = "The user rewound what you changed from their message \"$asked\" " +
                "on. These are back as they were before it: ${listed(result.restored)}. Read files again before you " +
                "edit them."))
        }
        return result
    }

    /** The user's messages from [messageId] on, whose turns rewinding to before it undoes. */
    private fun turnsFrom(conversation: Conversation, messageId: String): List<String> {
        val index = conversation.messages.indexOfFirst { it.id == messageId }
        if (index < 0)
            return emptyList()
        return conversation.messages.drop(index).filter { it.isUserTurn }.map { it.id }
    }

    /**
     * Cuts [conversation] back to before its message at [index]. Attachments only the removed part had go with it,
     * apart from [keep], and so do the checkpoints of its turns: what they changed is put back or stays.
     */
    private fun cut(conversation: Conversation, index: Int, title: String, keep: List<Part.Attachment>, note: String?) {
        val kept = conversation.messages.take(index)
        val removed = conversation.messages.drop(index)
        hub.store.save(conversation.copy(
            messages = kept,
            project = History.projectAt(kept),
            title = title,
            state = RunState.IDLE,
            error = null,
            updatedAt = System.currentTimeMillis(),
            pendingNote = note,
        ))
        val stillUsed = (kept.flatMap { it.attachments } + keep).mapTo(HashSet()) { it.id }
        removed.flatMap { it.attachments }
            .filter { it.id !in stillUsed }
            .forEach { hub.attachments.delete(conversation.id, it.id) }
        hub.checkpoints.discard(conversation.id, removed.filter { it.isUserTurn }.map { it.id })
    }

    private fun listed(items: List<String>): String =
        items.take(NOTE_ITEMS).joinToString() + if (items.size > NOTE_ITEMS) " and ${items.size - NOTE_ITEMS} more" else ""

    /** Sets when [conversationId] asks before running tools; null follows the settings. Applies to a run going on. */
    fun setApproval(conversationId: String, approval: ApprovalPolicy?) {
        // Called from the screen as the user picks; the write goes off the main thread.
        scope.launch(Dispatchers.IO) {
            if (hub.store.get(conversationId)?.approval == approval?.id)
                return@launch
            updateConversation(conversationId) { it.copy(approval = approval?.id) }
        }
    }

    fun approvalOf(conversationId: String): ApprovalPolicy =
        hub.store.get(conversationId)?.approval?.let(ApprovalPolicy::of) ?: hub.settings.approvalPolicy

    /** Continues a run that stopped: after a step limit, an error or the app being closed. */
    fun resume(conversationId: String) {
        if (isRunning(conversationId))
            return
        start(conversationId)
    }

    fun stop(conversationId: String) {
        jobs[conversationId]?.cancel(StopException())
    }

    /** Stops every run because Android ended the service that keeps them going; each can be continued later. */
    fun stopAllForTimeLimit() {
        for (job in jobs.values)
            job.cancel(StopException(byTimeLimit = true))
    }

    /** Answers a tool call that waits for the user; [always] allows that tool for the rest of the conversation. */
    fun resolveApproval(conversationId: String, callId: String, allow: Boolean, always: Boolean = false) {
        if (allow && always) {
            val conversation = hub.store.get(conversationId)
            val name = conversation?.messages?.lastOrNull { it.role == Role.ASSISTANT }?.toolCalls?.firstOrNull { it.id == callId }?.name
            if (name != null)
                alwaysAllowed.getOrPut(conversationId) { ConcurrentHashMap.newKeySet() } += name
        }
        approvals.remove(approvalKey(conversationId, callId))?.complete(allow)
    }

    fun delete(conversationId: String) {
        stop(conversationId)
        hub.store.delete(conversationId)
        hub.attachments.deleteAll(conversationId)
        alwaysAllowed.remove(conversationId)
    }

    private fun start(conversationId: String) {
        startedAt[conversationId] = System.currentTimeMillis()
        mDismissed.update { it - conversationId }
        setLive(conversationId) { LiveRun(conversationId, LiveRun.Phase.WAITING) }
        AgentService.start(hub.context)
        // Registered before it starts, so that a run that ends at once still clears what it set.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                run(conversationId)
            } finally {
                if (jobs.remove(conversationId, coroutineContext.job)) {
                    startedAt.remove(conversationId)
                    mDismissed.update { it - conversationId }
                    mLive.update { it - conversationId }
                }
            }
        }
        jobs[conversationId] = job
        job.start()
    }

    private suspend fun run(id: String) {
        updateConversation(id) { it.copy(state = RunState.RUNNING, error = null) }
        var steps = 0
        val maxSteps = hub.settings.maxSteps
        try {
            while (true) {
                val conversation = hub.store.get(id) ?: return
                // Calls left without a result, also after the app was killed halfway through a turn's tools.
                val open = History.openCalls(conversation.messages)
                if (open != null) {
                    runTools(id, open.first, open.second)
                    continue
                }
                // Notices are for the user; what counts is the last turn the model takes part in.
                val last = conversation.messages.lastOrNull { message -> message.parts.any { it !is Part.Notice } } ?: return
                when {
                    last.role == Role.ASSISTANT -> break
                    else -> {
                        if (steps >= maxSteps) {
                            appendNotice(id, NoticeKind.PAUSED, string(R.string.agent_notice_paused, maxSteps))
                            updateConversation(id) { it.copy(state = RunState.IDLE) }
                            return
                        }
                        steps++
                        val result = callModel(id)
                        if (isSilent(result)) {
                            // An empty end of turn, typically right after tool results: the model takes its work as
                            // done, and the same history gets the same empty answer however often it is resent. The
                            // turn is dropped and the model asked for its reply in a new user message, once in a row.
                            updateConversation(id) { it.copy(usage = it.usage + result.usage) }
                            if (isNudge(last)) {
                                appendNotice(id, NoticeKind.NO_REPLY, string(R.string.agent_notice_no_reply))
                                break
                            }
                            appendNudge(id)
                            continue
                        }
                        if (!handleResult(id, result))
                            break
                    }
                }
            }
            updateConversation(id) { it.copy(state = RunState.IDLE) }
            AgentNotifier.finished(hub, id)
        } catch (e: CancellationException) {
            val byTimeLimit = (e as? StopException)?.byTimeLimit == true
            withContext(NonCancellable) {
                answerOpenCalls(id, if (byTimeLimit) "Android stopped the run before this ran." else "The user stopped the run before this ran.")
                if (byTimeLimit)
                    appendNotice(id, NoticeKind.TIME_LIMIT, string(R.string.agent_notice_time_limit))
                else
                    appendNotice(id, NoticeKind.STOPPED, string(R.string.agent_notice_stopped))
                // A run Android stopped shows as cut short, like one the app was closed during.
                updateConversation(id) { it.copy(state = if (byTimeLimit) RunState.INTERRUPTED else RunState.IDLE) }
            }
            if (byTimeLimit)
                AgentNotifier.failed(hub, id, string(R.string.agent_notice_time_limit))
            if (e !is StopException)
                throw e
        } catch (e: Throwable) {
            // Errors too: a user script can overflow the stack while it compiles.
            LOG.error { "Agent run failed: $e" }
            val message = describe(e)
            answerOpenCalls(id, "Not run: the request failed.")
            appendNotice(id, NoticeKind.ERROR, message)
            updateConversation(id) { it.copy(state = RunState.ERROR, error = message) }
            AgentNotifier.failed(hub, id, message)
        }
    }

    /** Sends the conversation to the model, retrying overloads and network failures. */
    private suspend fun callModel(id: String): LlmResult {
        val kind = hub.settings.activeProvider()
            ?: throw SignInRequiredException(string(R.string.agent_error_setup))
        val model = hub.settings.model(kind) ?: throw SignInRequiredException(string(R.string.agent_error_model, kind.displayName))
        val client = hub.client(kind)
        var attempt = 0
        while (true) {
            val conversation = hub.store.get(id) ?: throw IOException("The conversation was deleted")
            val request = LlmRequest(
                conversationId = id,
                model = model,
                system = SystemPrompt.TEXT,
                tools = hub.tools.all.map { it.spec },
                messages = conversation.messages,
                effort = hub.settings.effort(kind),
                maxOutputTokens = MAX_OUTPUT_TOKENS,
                attachments = hub.attachments,
            )
            setLive(id) { LiveRun(id, LiveRun.Phase.WAITING, retry = attempt) }
            try {
                val result = client.complete(request, sink(id))
                updateConversation(id) { it.copy(provider = kind.id, model = result.model ?: model) }
                return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isRetryable(e) || attempt >= MAX_RETRIES)
                    throw e
                val wait = (e as? HttpStatusException)?.retryAfterMillis ?: (RETRY_BASE_MILLIS shl attempt)
                attempt++
                LOG.info { "Retrying agent request ($attempt/$MAX_RETRIES) in ${wait}ms: $e" }
                setLive(id) { LiveRun(id, LiveRun.Phase.RETRYING, retry = attempt, error = describe(e)) }
                delay(wait.coerceAtMost(MAX_RETRY_WAIT_MILLIS))
            }
        }
    }

    /** A turn with nothing for the user and nothing to run, such as an empty end_turn. */
    private fun isSilent(result: LlmResult): Boolean =
        result.stopReason != StopReason.REFUSAL && result.stopReason != StopReason.MAX_TOKENS &&
            result.parts.none { it is Part.ToolCall || (it is Part.Text && it.text.isNotBlank()) }

    private fun isNudge(message: ChatMessage): Boolean =
        message.role == Role.USER && message.parts.all { it is Part.Context && it.label == Part.Context.NUDGE }

    private fun appendNudge(id: String) {
        val now = System.currentTimeMillis()
        val nudge = ChatMessage(newId("m"), Role.USER, listOf(Part.Context(Part.Context.NUDGE, NUDGE_TEXT)), now)
        updateConversation(id) { it.copy(messages = it.messages + nudge, updatedAt = now) }
    }

    /** Saves the model's turn; false when the run ends with it. */
    private fun handleResult(id: String, result: LlmResult): Boolean {
        val now = System.currentTimeMillis()
        if (result.stopReason == StopReason.REFUSAL) {
            // A declined turn is dropped, partial output included; the user can rephrase.
            appendNotice(id, NoticeKind.REFUSAL, result.refusal?.let { string(R.string.agent_notice_refusal_reason, it) } ?: string(R.string.agent_notice_refusal))
            return false
        }
        val message = ChatMessage(newId("m"), Role.ASSISTANT, result.parts, now, raw = result.raw, model = result.model, usage = result.usage)
        updateConversation(id) {
            it.copy(messages = it.messages + message, usage = it.usage + result.usage, updatedAt = now)
        }
        val calls = message.toolCalls
        if (calls.isNotEmpty() && result.stopReason == StopReason.MAX_TOKENS) {
            // The arguments were cut off; running them would act on half a file.
            addResults(id, message.id, calls.map { call ->
                Part.ToolResult(call.id, call.name, "Your output hit the length limit before this call was complete, so it " +
                    "didn't run. Split the work: edit smaller parts of files instead of rewriting them whole.", isError = true)
            })
            return true
        }
        if (calls.isEmpty() && result.stopReason == StopReason.MAX_TOKENS)
            appendNotice(id, NoticeKind.INFO, string(R.string.agent_notice_length))
        return calls.isNotEmpty()
    }

    /** Runs [calls] of [turn], saving each result as it comes, so that a stop or a crash keeps what already ran. */
    private suspend fun runTools(id: String, turn: ChatMessage, calls: List<Part.ToolCall>) {
        for (call in calls)
            addResults(id, turn.id, listOf(runTool(id, turn.id, call)))
    }

    private suspend fun runTool(id: String, messageId: String, call: Part.ToolCall): Part.ToolResult {
        val tool = hub.tools.find(call.name)
            ?: return Part.ToolResult(call.id, call.name, "Unknown tool: ${call.name}", isError = true)
        val args: JsonObject = RequestContent.parseArguments(call.arguments)
            ?: return Part.ToolResult(call.id, call.name, RequestContent.invalidJsonResult(call.arguments), isError = true,
                summary = "Invalid arguments")
        if (needsApproval(id, tool.risk, tool.name)) {
            val description = runCatching { tool.describe(args) }.getOrDefault(tool.name)
            val preview = tool.preview?.let { preview -> withContext(Dispatchers.IO) { runCatching { preview(args) }.getOrNull() } }
            val key = approvalKey(id, call.id)
            preview?.let { approvalPreviews[key] = it }
            val allowed = try {
                askApproval(id, messageId, call, description)
            } finally {
                approvalPreviews.remove(key)
            }
            if (!allowed)
                return Part.ToolResult(call.id, call.name, "The user denied this action. Don't retry it unless the user asks.",
                    isError = true, summary = "Denied")
        }
        setLive(id) { (it ?: LiveRun(id, LiveRun.Phase.RUNNING_TOOL)).copy(phase = LiveRun.Phase.RUNNING_TOOL, tool = tool.name) }
        val context = object : ToolContext {
            override val conversationId: String = id
            override fun attachmentFile(attachmentId: String): File? = hub.attachments.file(id, attachmentId)
            override fun outbox(): File = hub.attachments.outbox(id)
            override fun addAttachment(name: String, mimeType: String, bytes: ByteArray): Part.Attachment =
                hub.attachments.add(id, name, mimeType, bytes)
            // A turn's changes are kept under the user's message that started it.
            override val checkpoint by lazy {
                val turn = hub.store.get(id)?.messages?.lastOrNull { it.isUserTurn }?.id ?: messageId
                hub.checkpoints.recorder(id, turn)
            }
        }
        val output = try {
            tool.run(context, args)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ToolException) {
            ToolOutput.error(e.message ?: e.toString())
        } catch (e: Throwable) {
            // Throwable: compiling or messaging a script can end in a StackOverflowError or a LinkageError.
            LOG.warn { "Tool ${tool.name} failed: $e" }
            ToolOutput.error("${tool.name} failed: ${e.message ?: e}")
        }
        return Part.ToolResult(call.id, call.name, cap(output.text), output.isError, output.summary, output.card, output.images,
            output.diff)
    }

    private fun needsApproval(id: String, risk: ToolRisk, name: String): Boolean {
        if (alwaysAllowed[id]?.contains(name) == true)
            return false
        return when (approvalOf(id)) {
            ApprovalPolicy.AUTO            -> false
            ApprovalPolicy.ASK_DESTRUCTIVE -> risk == ToolRisk.DESTRUCTIVE
            ApprovalPolicy.ASK_WRITES      -> risk != ToolRisk.READ
        }
    }

    private suspend fun askApproval(id: String, messageId: String, call: Part.ToolCall, description: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        approvals[approvalKey(id, call.id)] = deferred
        pendingApprovals[id] = call.id to description
        setApproval(id, messageId, call.id, Approval.PENDING)
        updateConversation(id) { it.copy(state = RunState.WAITING_APPROVAL) }
        setLive(id) { LiveRun(id, LiveRun.Phase.APPROVAL, tool = call.name) }
        AgentNotifier.approvalNeeded(hub, id, call.id, description)
        try {
            val allowed = deferred.await()
            setApproval(id, messageId, call.id, if (allowed) Approval.ALLOWED else Approval.DENIED)
            return allowed
        } finally {
            approvals.remove(approvalKey(id, call.id))
            pendingApprovals.remove(id)
            AgentNotifier.cancelApproval(hub, id)
            updateConversation(id) { it.copy(state = RunState.RUNNING) }
        }
    }

    /** Records the approval on the call, for the chat; the model never sees it, so the history it reads stays the same. */
    private fun setApproval(id: String, messageId: String, callId: String, approval: Approval) {
        updateConversation(id) { conversation ->
            conversation.copy(messages = conversation.messages.map { message ->
                if (message.id != messageId)
                    message
                else
                    message.copy(parts = message.parts.map { part ->
                        if (part is Part.ToolCall && part.id == callId) part.copy(approval = approval) else part
                    })
            })
        }
    }

    /** Gives the calls of the last assistant turn that have no result one, so that the history stays valid. */
    private fun answerOpenCalls(id: String, reason: String) {
        val (turn, open) = hub.store.get(id)?.messages?.let(History::openCalls) ?: return
        addResults(id, turn.id, open.map { Part.ToolResult(it.id, it.name, reason, isError = true, summary = "Not run") })
    }

    private fun addResults(id: String, turnId: String, results: List<Part.ToolResult>) {
        val now = System.currentTimeMillis()
        updateConversation(id) {
            val messages = History.addResults(it.messages, turnId, results) { ChatMessage(newId("t"), Role.TOOL, results, now) }
            it.copy(messages = messages, updatedAt = now)
        }
    }

    private fun appendNotice(id: String, kind: NoticeKind, text: String) {
        val now = System.currentTimeMillis()
        updateConversation(id) {
            it.copy(messages = it.messages + ChatMessage(newId("n"), Role.ASSISTANT, listOf(Part.Notice(kind, text)), now), updatedAt = now)
        }
    }

    private fun updateConversation(id: String, block: (Conversation) -> Conversation) {
        runCatching { hub.store.update(id, block) }
            .onFailure { LOG.warn { "Couldn't save conversation $id: $it" } }
    }

    private fun sink(conversationId: String) = object : StreamSink {
        override fun onText(delta: String) = setLive(conversationId) { current ->
            val run = current ?: LiveRun(conversationId, LiveRun.Phase.WRITING)
            run.copy(phase = LiveRun.Phase.WRITING, text = run.text + delta, retry = 0, error = null)
        }

        override fun onThinking(delta: String) = setLive(conversationId) { current ->
            val run = current ?: LiveRun(conversationId, LiveRun.Phase.THINKING)
            run.copy(phase = if (run.phase == LiveRun.Phase.WRITING) run.phase else LiveRun.Phase.THINKING, thinking = run.thinking + delta)
        }

        override fun onToolCall(id: String, name: String) = setLive(conversationId) { current ->
            (current ?: LiveRun(conversationId, LiveRun.Phase.CALLING_TOOL)).copy(phase = LiveRun.Phase.CALLING_TOOL, tool = name)
        }
    }

    private fun setLive(id: String, block: (LiveRun?) -> LiveRun) {
        mLive.update { map -> map + (id to block(map[id])) }
    }

    private fun switchPart(project: String?): Part.Context =
        if (project != null)
            Part.Context(project, "The user switched this conversation to the StarLight project \"$project\"; " +
                "unless they say otherwise, their requests from here on are about it.")
        else
            Part.Context(Part.Context.NO_PROJECT, "The user detached this conversation from any project; don't assume one " +
                "from here on.")

    private fun contextParts(project: String?): List<Part> {
        val parts = mutableListOf<Part>()
        if (project != null)
            parts += Part.Context(project, "This conversation was opened from the StarLight project \"$project\"; " +
                "unless the user says otherwise, their requests are about it.")
        hub.settings.customInstructions.takeIf(String::isNotBlank)?.let {
            parts += Part.Context(Part.Context.INSTRUCTIONS, "The user's standing instructions for you:\n$it")
        }
        return parts
    }

    private fun titleOf(text: String, attachments: List<Part.Attachment>): String {
        val line = text.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
            ?: attachments.firstOrNull()?.name
            ?: "New conversation"
        return if (line.length > TITLE_LENGTH) line.take(TITLE_LENGTH - 1) + "…" else line
    }

    private fun isRetryable(e: Exception): Boolean = when (e) {
        is SignInRequiredException -> false
        is HttpStatusException     -> e.isRetryable && NON_RETRYABLE_CODES.none { it in e.body }
        is StreamErrorException    -> e.isRetryable
        is IOException             -> true
        else                       -> false
    }

    /** A message for the user from an exception, with the provider's own message when it gave one. */
    fun describe(e: Throwable): String = when (e) {
        is HttpStatusException -> {
            val detail = runCatching {
                val root = AgentJson.parseToJsonElement(e.body).jsonObject
                val error = root["error"]
                (error as? JsonObject)?.string("message") ?: (error as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: root.string("detail") ?: root.string("message")
            }.getOrNull() ?: e.body.take(300)
            when (e.code) {
                401, 403 -> string(R.string.agent_error_auth, e.code, detail)
                429      -> string(R.string.agent_error_limit, detail)
                else     -> string(R.string.agent_error_http, e.code, detail)
            }
        }
        is SignInRequiredException -> e.message ?: "Sign in again."
        is StreamErrorException    -> e.message ?: "The provider reported an error."
        is java.net.UnknownHostException -> string(R.string.agent_error_network)
        is java.net.SocketTimeoutException -> string(R.string.agent_error_timeout)
        // Connection failures carry the HTTP stack's own wording; say what happened, keep it as detail.
        is IOException -> string(R.string.agent_error_connection, e.message ?: e.javaClass.simpleName)
        else -> e.message ?: e.toString()
    }

    private fun string(id: Int, vararg args: Any): String =
        hub.context.getString(id, *args)

    /** Ends a run on purpose: the user stopped it, or Android ended the service that kept it going. */
    private class StopException(val byTimeLimit: Boolean = false) :
        CancellationException(if (byTimeLimit) "Stopped by Android's time limit" else "Stopped by the user")

    private companion object {
        const val MAX_OUTPUT_TOKENS = 64_000
        const val MAX_RETRIES = 4
        const val RETRY_BASE_MILLIS = 2_000L
        const val MAX_RETRY_WAIT_MILLIS = 60_000L
        const val MAX_TOOL_OUTPUT = 50_000
        const val TITLE_LENGTH = 48
        /** How much of the user's message a note about rewinding quotes, and how many changes it lists. */
        const val NOTE_QUOTE_CHARS = 60
        const val NOTE_ITEMS = 20
        const val NUDGE_TEXT = "Your last turn ended without a reply, so the user saw nothing from you. Continue: " +
            "finish anything that is left, then reply to the user."

        /** ChatGPT plan errors that retrying can't fix. */
        val NON_RETRYABLE_CODES = listOf("subscription_sharing_", "chatpass_v2_", "usage_not_included", "insufficient_quota")

        val NOTHING_REWOUND = Rewind.Result(emptyList(), emptyList(), emptySet(), emptyMap())

        fun approvalKey(conversationId: String, callId: String) = "$conversationId/$callId"

        fun newId(prefix: String) = prefix + UUID.randomUUID().toString().replace("-", "").take(12)

        fun cap(text: String): String =
            if (text.length <= MAX_TOOL_OUTPUT) text
            else text.take(MAX_TOOL_OUTPUT) + "\n... (cut: ${text.length - MAX_TOOL_OUTPUT} more characters)"
    }
}
