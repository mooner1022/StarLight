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
import dev.mooner.starlight.agent.provider.ProviderKind
import dev.mooner.starlight.agent.provider.RequestContent
import dev.mooner.starlight.agent.provider.StopReason
import dev.mooner.starlight.agent.provider.StreamErrorException
import dev.mooner.starlight.agent.provider.StreamSink
import dev.mooner.starlight.agent.provider.string
import dev.mooner.starlight.agent.tools.ToolContext
import dev.mooner.starlight.agent.tools.ToolException
import dev.mooner.starlight.agent.tools.ToolOutput
import dev.mooner.starlight.agent.tools.ToolRisk
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    private val mLive = MutableStateFlow<Map<String, LiveRun>>(emptyMap())
    val live: StateFlow<Map<String, LiveRun>> = mLive.asStateFlow()

    /** The conversation shown on screen, which needs no notification when it finishes. */
    @Volatile
    var visibleConversation: String? = null
        private set

    /** Approvals waiting for the user, by conversation: the call and what it would do. */
    private val pendingApprovals = ConcurrentHashMap<String, Pair<String, String>>()

    fun chatShown(conversationId: String) {
        visibleConversation = conversationId
        AgentNotifier.cancelApproval(hub, conversationId)
    }

    /** The chat went away; a question it was showing moves to a notification. */
    fun chatHidden(conversationId: String) {
        if (visibleConversation == conversationId)
            visibleConversation = null
        pendingApprovals[conversationId]?.let { (callId, description) ->
            AgentNotifier.approvalNeeded(hub, conversationId, callId, description)
        }
    }

    fun isRunning(conversationId: String): Boolean =
        jobs[conversationId]?.isActive == true

    fun newConversationId(): String =
        "c" + UUID.randomUUID().toString().replace("-", "").take(16)

    /**
     * Adds the user's message to [conversationId], creating the conversation if it doesn't exist, and runs it.
     * [project] is only used by a new conversation, which then starts with that project's context.
     */
    fun send(conversationId: String, text: String, attachments: List<Part.Attachment>, project: String? = null) {
        check(!isRunning(conversationId)) { "The conversation is still running" }
        val now = System.currentTimeMillis()
        val existing = hub.store.get(conversationId)
        val parts = buildList {
            if (existing == null || existing.messages.none { it.role == Role.USER })
                addAll(contextParts(project ?: existing?.project))
            addAll(attachments)
            if (text.isNotBlank())
                add(Part.Text(text.trim()))
        }
        val message = ChatMessage(newId("m"), Role.USER, parts, now)
        val conversation = existing ?: Conversation(
            id = conversationId,
            title = titleOf(text, attachments),
            createdAt = now,
            updatedAt = now,
            project = project,
        )
        hub.store.save(conversation.copy(messages = conversation.messages + message, updatedAt = now, error = null))
        start(conversationId)
    }

    /** Continues a run that stopped: after a step limit, an error or the app being closed. */
    fun resume(conversationId: String) {
        if (isRunning(conversationId))
            return
        start(conversationId)
    }

    fun stop(conversationId: String) {
        jobs[conversationId]?.cancel(StopException())
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
        setLive(conversationId) { LiveRun(conversationId, LiveRun.Phase.WAITING) }
        AgentService.start(hub.context)
        val job = scope.launch {
            try {
                run(conversationId)
            } finally {
                jobs.remove(conversationId)
                mLive.update { it - conversationId }
            }
        }
        jobs[conversationId] = job
    }

    private suspend fun run(id: String) {
        updateConversation(id) { it.copy(state = RunState.RUNNING, error = null) }
        var steps = 0
        val maxSteps = hub.settings.maxSteps
        try {
            while (true) {
                val conversation = hub.store.get(id) ?: return
                // Notices are for the user; what counts is the last turn the model takes part in.
                val last = conversation.messages.lastOrNull { message -> message.parts.any { it !is Part.Notice } } ?: return
                when {
                    last.role == Role.ASSISTANT && last.toolCalls.isNotEmpty() -> runTools(id, last)
                    last.role == Role.ASSISTANT -> break
                    else -> {
                        if (steps >= maxSteps) {
                            appendNotice(id, NoticeKind.INFO, string(R.string.agent_notice_paused, maxSteps))
                            updateConversation(id) { it.copy(state = RunState.IDLE) }
                            return
                        }
                        steps++
                        val result = callModel(id)
                        if (!handleResult(id, result))
                            break
                    }
                }
            }
            updateConversation(id) { it.copy(state = RunState.IDLE) }
            AgentNotifier.finished(hub, id)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                answerOpenCalls(id, "The user stopped the run before this ran.")
                appendNotice(id, NoticeKind.STOPPED, string(R.string.agent_notice_stopped))
                updateConversation(id) { it.copy(state = RunState.IDLE) }
            }
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
            appendResults(id, calls.map { call ->
                Part.ToolResult(call.id, call.name, "Your output hit the length limit before this call was complete, so it " +
                    "didn't run. Split the work: edit smaller parts of files instead of rewriting them whole.", isError = true)
            })
            return true
        }
        if (calls.isEmpty() && result.stopReason == StopReason.MAX_TOKENS)
            appendNotice(id, NoticeKind.INFO, string(R.string.agent_notice_length))
        return calls.isNotEmpty()
    }

    private suspend fun runTools(id: String, message: ChatMessage) {
        val results = mutableListOf<Part.ToolResult>()
        for (call in message.toolCalls) {
            results += runTool(id, message.id, call)
        }
        appendResults(id, results)
    }

    private suspend fun runTool(id: String, messageId: String, call: Part.ToolCall): Part.ToolResult {
        val tool = hub.tools.find(call.name)
            ?: return Part.ToolResult(call.id, call.name, "Unknown tool: ${call.name}", isError = true)
        val args: JsonObject = RequestContent.parseArguments(call.arguments)
            ?: return Part.ToolResult(call.id, call.name, RequestContent.invalidJsonResult(call.arguments), isError = true,
                summary = "Invalid arguments")
        if (needsApproval(id, tool.risk, tool.name)) {
            val description = runCatching { tool.describe(args) }.getOrDefault(tool.name)
            val allowed = askApproval(id, messageId, call, description)
            if (!allowed)
                return Part.ToolResult(call.id, call.name, "The user denied this action. Don't retry it unless the user asks.",
                    isError = true, summary = "Denied")
        }
        setLive(id) { (it ?: LiveRun(id, LiveRun.Phase.RUNNING_TOOL)).copy(phase = LiveRun.Phase.RUNNING_TOOL, tool = tool.name) }
        val context = object : ToolContext {
            override val conversationId: String = id
            override fun attachmentFile(attachmentId: String): File? = hub.attachments.file(id, attachmentId)
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
        return Part.ToolResult(call.id, call.name, cap(output.text), output.isError, output.summary)
    }

    private fun needsApproval(id: String, risk: ToolRisk, name: String): Boolean {
        if (alwaysAllowed[id]?.contains(name) == true)
            return false
        return when (hub.settings.approvalPolicy) {
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

    /** Gives every call of the last assistant turn that has no result one, so that the history stays valid. */
    private fun answerOpenCalls(id: String, reason: String) {
        val conversation = hub.store.get(id) ?: return
        val last = conversation.messages.lastOrNull { message -> message.parts.any { it !is Part.Notice } } ?: return
        if (last.role != Role.ASSISTANT || last.toolCalls.isEmpty())
            return
        appendResults(id, last.toolCalls.map { Part.ToolResult(it.id, it.name, reason, isError = true, summary = "Not run") })
    }

    private fun appendResults(id: String, results: List<Part.ToolResult>) {
        val now = System.currentTimeMillis()
        updateConversation(id) { it.copy(messages = it.messages + ChatMessage(newId("t"), Role.TOOL, results, now), updatedAt = now) }
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

    private fun contextParts(project: String?): List<Part> {
        val parts = mutableListOf<Part>()
        if (project != null)
            parts += Part.Context(project, "This conversation was opened from the StarLight project \"$project\"; " +
                "unless the user says otherwise, their requests are about it.")
        hub.settings.customInstructions.takeIf(String::isNotBlank)?.let {
            parts += Part.Context("instructions", "The user's standing instructions for you:\n$it")
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

    private class StopException : CancellationException("Stopped by the user")

    private companion object {
        const val MAX_OUTPUT_TOKENS = 64_000
        const val MAX_RETRIES = 4
        const val RETRY_BASE_MILLIS = 2_000L
        const val MAX_RETRY_WAIT_MILLIS = 60_000L
        const val MAX_TOOL_OUTPUT = 50_000
        const val TITLE_LENGTH = 48

        /** ChatGPT plan errors that retrying can't fix. */
        val NON_RETRYABLE_CODES = listOf("subscription_sharing_", "chatpass_v2_", "usage_not_included", "insufficient_quota")

        fun approvalKey(conversationId: String, callId: String) = "$conversationId/$callId"

        fun newId(prefix: String) = prefix + UUID.randomUUID().toString().replace("-", "").take(12)

        fun cap(text: String): String =
            if (text.length <= MAX_TOOL_OUTPUT) text
            else text.take(MAX_TOOL_OUTPUT) + "\n... (cut: ${text.length - MAX_TOOL_OUTPUT} more characters)"
    }
}
