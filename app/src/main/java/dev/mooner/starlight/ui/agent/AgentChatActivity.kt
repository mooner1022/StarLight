package dev.mooner.starlight.ui.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.ApprovalPolicy
import dev.mooner.starlight.agent.model.Card
import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Conversation
import dev.mooner.starlight.agent.model.NoticeKind
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Plan
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.model.RunState
import dev.mooner.starlight.agent.runtime.History
import dev.mooner.starlight.agent.runtime.LiveRun
import dev.mooner.starlight.agent.store.Rewind
import dev.mooner.starlight.databinding.ActivityAgentChatBinding
import dev.mooner.starlight.ui.common.showSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** One conversation with the agent. Runs go on when this screen closes; it only shows them. */
class AgentChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgentChatBinding
    private lateinit var composer: Composer
    private lateinit var adapter: ChatAdapter
    private lateinit var planBoard: PlanBoard
    /** The conversation [plan] was read from, and whether it was running; it is read again only when they change. */
    private var planSource: Conversation? = null
    private var planRunning = false
    private var plan: Plan? = null
    private val hub by lazy { AgentHub.get(this) }

    private lateinit var conversationId: String
    /** The project this screen was opened for, used until the conversation has one of its own. */
    private var openedFor: String? = null
    /** Whether the composer holds the conversation's project and approval yet. */
    private var sessionLoaded = false
    /** Tool groups the user opened; the others stay collapsed. */
    private val expandedGroups = hashSetOf<String>()
    private var shownConversation: Conversation? = null
    private var shownRun: LiveRun? = null
    /** The file waiting for the user to pick where to save it. */
    private var pendingSave: String? = null

    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { composer.onPicked(it) }
    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { composer.onPicked(it) }
    private val saveFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val source = pendingSave?.let(::File)
        pendingSave = null
        val target = result.data?.data
        if (result.resultCode == RESULT_OK && target != null && source != null)
            copyOut(source, target)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgentChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        conversationId = savedInstanceState?.getString(EXTRA_CONVERSATION)
            ?: intent.getStringExtra(EXTRA_CONVERSATION)
            ?: hub.runtime.newConversationId()
        openedFor = intent.getStringExtra(EXTRA_PROJECT)
        pendingSave = savedInstanceState?.getString(KEY_PENDING_SAVE)
        planBoard = PlanBoard(binding.plan, onGo = ::carryOutPlan)
        planBoard.collapsed = savedInstanceState?.getBoolean(KEY_PLAN_COLLAPSED) == true

        setUpWindow()
        binding.stars.density = 0.35f
        binding.stars.brightness = 0.8f
        binding.back.setOnClickListener { finish() }
        binding.titleArea.setOnClickListener { ModelSheet.show(this) }
        binding.menu.setOnClickListener { showMenu() }

        adapter = ChatAdapter(this, AgentUi.markdown(this), { conversationId }, object : ChatActions {
            override fun onApproval(callId: String, allow: Boolean, always: Boolean) =
                hub.runtime.resolveApproval(conversationId, callId, allow, always)

            override fun onToggleGroup(id: String) {
                if (!expandedGroups.add(id))
                    expandedGroups.remove(id)
                render(shownConversation, shownRun, follow = false)
            }

            override fun onSaveFile(card: Card, file: File) {
                pendingSave = file.path
                saveFile.launch(Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(card.mimeType ?: "application/octet-stream")
                    .putExtra(Intent.EXTRA_TITLE, card.name))
            }

            override fun onEdit(messageId: String) =
                startEditing(messageId)

            override fun onRewind(messageId: String) =
                confirmRewind(messageId)

            override fun onAnswer(callId: String, answers: List<String>?) {
                hub.runtime.answer(conversationId, callId, answers)
            }
        })
        binding.messages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messages.adapter = adapter
        binding.messages.itemAnimator = null

        composer = Composer(
            binding = binding.composer,
            scope = lifecycleScope,
            conversationId = { conversationId },
            pickImages = pickImages,
            pickFiles = pickFiles,
            activity = { this },
            onSend = ::send,
            onStop = { hub.runtime.stop(conversationId) },
            onSessionChanged = ::onSessionChanged,
        )
        intent.getStringExtra(EXTRA_TEXT)?.let { composer.text = it }
        if (savedInstanceState?.getBoolean(KEY_SESSION) == true) {
            composer.project = savedInstanceState.getString(KEY_PROJECT)
            composer.approval = savedInstanceState.getString(KEY_APPROVAL)?.let(ApprovalPolicy::of)
            sessionLoaded = true
        }
        binding.resumeButton.setOnClickListener { hub.runtime.resume(conversationId) }

        observe()
        if (hub.store.get(conversationId) == null)
            composer.focus()
    }

    /**
     * This screen is single-top, so a notification for another conversation, tapped while it is on top, arrives here
     * instead of opening a screen of its own. It then switches to that conversation.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val target = intent.getStringExtra(EXTRA_CONVERSATION) ?: hub.runtime.newConversationId()
        if (target != conversationId) {
            // onPause already hid the old conversation; onResume shows this one.
            conversationId = target
            openedFor = intent.getStringExtra(EXTRA_PROJECT)
            sessionLoaded = false
            expandedGroups.clear()
            planBoard.reset()
            planSource = null
            composer.clear()
            render(hub.store.get(conversationId), hub.runtime.live.value[conversationId])
        }
        intent.getStringExtra(EXTRA_TEXT)?.let { composer.text = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_CONVERSATION, conversationId)
        outState.putString(KEY_PENDING_SAVE, pendingSave)
        outState.putBoolean(KEY_PLAN_COLLAPSED, planBoard.collapsed)
        if (sessionLoaded) {
            outState.putBoolean(KEY_SESSION, true)
            outState.putString(KEY_PROJECT, composer.project)
            outState.putString(KEY_APPROVAL, composer.approval?.id)
        }
    }

    override fun onResume() {
        super.onResume()
        hub.runtime.chatShown(conversationId)
        updateSubtitle()
    }

    override fun onPause() {
        hub.runtime.chatHidden(conversationId)
        super.onPause()
    }

    private fun setUpWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // The night header runs under the status bar, whose icons stay light on it.
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = getColor(R.color.agent_bg)
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) !=
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.headerContent.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            binding.bottomArea.updatePadding(bottom = maxOf(bars.bottom, ime.bottom) + AgentUi.dp(this, 10),
                left = bars.left + AgentUi.dp(this, 12), right = bars.right + AgentUi.dp(this, 12))
            planBoard.imeShown = insets.isVisible(WindowInsetsCompat.Type.ime())
            insets
        }
    }

    @OptIn(FlowPreview::class)
    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Any conversation changing bumps the store's count; the same object back means this one didn't.
                val conversations = hub.store.changes.map { hub.store.get(conversationId) }.distinctUntilChanged { a, b -> a === b }
                val live = hub.runtime.live.map { it[conversationId] }.sample(LIVE_INTERVAL_MILLIS)
                combine(conversations, live) { conversation, run -> conversation to run }
                    .collect { (conversation, run) -> render(conversation, run) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                hub.settings.version.collect { updateSubtitle() }
            }
        }
    }

    /** [follow] keeps the newest row in view as the chat grows; opening or closing a group doesn't. */
    private fun render(conversation: Conversation?, run: LiveRun?, follow: Boolean = true) {
        shownConversation = conversation
        shownRun = run
        loadSession(conversation)
        binding.title.text = conversation?.title ?: getString(R.string.agent_new_chat)
        val running = hub.runtime.isRunning(conversationId)
        composer.isRunning = running
        val project = composer.project
        composer.setHint(when {
            conversation == null && project != null -> getString(R.string.agent_project_prompt_hint, project)
            conversation == null                    -> getString(R.string.agent_composer_hint)
            else                                    -> getString(R.string.agent_composer_hint_followup)
        })
        val rows = conversation?.let { ChatRows.build(hub, it, run, expandedGroups) }.orEmpty()
        val atBottom = !binding.messages.canScrollVertically(1)
        val grew = rows.size > adapter.itemCount
        adapter.submitList(rows) {
            if (follow && rows.isNotEmpty() && (atBottom || grew))
                binding.messages.scrollToPosition(rows.size - 1)
        }
        updateResumeBar(conversation, running)
        updateSubtitle()
        // While the agent waits on a decision, the question takes the place of the field.
        binding.composer.root.isVisible = hub.runtime.pendingQuestion(conversationId) == null
        if (conversation !== planSource || running != planRunning) {
            planSource = conversation
            planRunning = running
            plan = conversation?.let { Plan.shown(it.messages, running) }
        }
        planBoard.show(plan, running, canGo = conversation?.approval?.let(ApprovalPolicy::of) == ApprovalPolicy.PLAN)
    }

    /** Fills the composer with the conversation's project and approval, once. */
    private fun loadSession(conversation: Conversation?) {
        if (sessionLoaded)
            return
        composer.project = conversation?.project ?: openedFor.takeIf { conversation == null }
        composer.approval = conversation?.approval?.let(ApprovalPolicy::of)
        sessionLoaded = true
    }

    /** Approval applies right away, to a run going on too; a project change goes with the next message. */
    private fun onSessionChanged() {
        if (sessionLoaded)
            hub.runtime.setApproval(conversationId, composer.approval)
    }

    private fun copyOut(source: File, target: Uri) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(target)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                        ?: throw IOException("No output stream")
                }
            }.onSuccess {
                Toast.makeText(this@AgentChatActivity, getString(R.string.agent_card_saved, source.name), Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(this@AgentChatActivity, getString(R.string.agent_card_save_failed, e.message ?: e.toString()), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateResumeBar(conversation: Conversation?, running: Boolean) {
        val reason = if (conversation == null || running) null else resumeReason(conversation)
        binding.resumeBar.isVisible = reason != null
        if (reason != null)
            binding.resumeText.text = reason
    }

    /** Why the conversation stopped before the model answered, or null when continuing has nothing to offer. */
    private fun resumeReason(conversation: Conversation): String? {
        val messages = conversation.messages
        val lastTurn = messages.indexOfLast { message -> message.compaction == null && message.parts.any { it !is Part.Notice } }
        val last = messages.getOrNull(lastTurn) ?: return null
        if (last.role == Role.ASSISTANT && last.toolCalls.isEmpty())
            return null
        // The notices after the last turn say how the run ended.
        val notices = messages.drop(lastTurn + 1).flatMap { it.parts }.filterIsInstance<Part.Notice>().map { it.kind }
        return when {
            NoticeKind.TIME_LIMIT in notices           -> getString(R.string.agent_resume_time_limit)
            // The error itself is in the chat right above.
            conversation.state == RunState.ERROR       -> getString(R.string.agent_resume_error)
            conversation.state == RunState.INTERRUPTED -> getString(R.string.agent_resume_interrupted)
            // Asking again would end the same way; the notice says so, and the user can write instead.
            NoticeKind.NO_REPLY in notices || NoticeKind.REFUSAL in notices -> null
            NoticeKind.STOPPED in notices              -> getString(R.string.agent_resume_stopped)
            // Before the step limit had its own kind, its notice was an INFO one.
            NoticeKind.PAUSED in notices || NoticeKind.INFO in notices -> getString(R.string.agent_resume_step_limit)
            // Ended on tool results with nothing said, as older versions did when the model gave an empty answer.
            else                                       -> getString(R.string.agent_resume_no_reply)
        }
    }

    private fun updateSubtitle() {
        val model = AgentUi.modelSummary(this, hub) ?: getString(R.string.agent_subtitle_setup)
        // How full the model's window is, once it is worth knowing.
        val use = shownConversation?.let(hub.runtime::contextUse)?.takeIf { it >= CONTEXT_SHOWN_FROM }
        binding.subtitle.text = if (use == null) model else getString(R.string.agent_context_use, model, (use * 100).toInt().coerceAtMost(100))
    }

    private fun send(text: String, attachments: List<Part.Attachment>): Boolean {
        if (hub.settings.activeProvider() == null) {
            startActivity(Intent(this, AgentSettingsActivity::class.java))
            return false
        }
        // During a run the message joins it; a run that is just ending keeps it in the field to send again.
        if (hub.runtime.isRunning(conversationId)) {
            if (!hub.runtime.queue(conversationId, text, attachments))
                return false
            render(shownConversation, shownRun)
            return true
        }
        val editing = composer.editingMessage
        if (editing != null && hub.store.get(conversationId)?.messages?.any { it.id == editing } == true) {
            val changes = hub.runtime.rewindable(conversationId, editing)
            if (changes.isNotEmpty()) {
                // The field keeps the message until the user picks.
                confirmEditRewind(editing, text, attachments, changes.size)
                return false
            }
            hub.runtime.resend(conversationId, editing, text, attachments, composer.project, composer.approval)
        } else {
            hub.runtime.send(conversationId, text, attachments, composer.project, composer.approval)
        }
        return true
    }

    /** The user approved the plan made in plan mode: the agent carries it out. */
    private fun carryOutPlan() {
        if (hub.runtime.isRunning(conversationId))
            return
        composer.approval = null
        hub.runtime.carryOutPlan(conversationId, getString(R.string.agent_plan_go_message), composer.project)
    }

    /** Rewriting a message the agent changed things after: asks whether to put them back too. */
    private fun confirmEditRewind(messageId: String, text: String, attachments: List<Part.Attachment>, changes: Int) {
        val project = composer.project
        val approval = composer.approval
        showSheet {
            title = getString(R.string.agent_edit_rewind_title)
            icon = R.drawable.ic_agent_rewind
            message = getString(R.string.agent_edit_rewind_message, changes)
            note = getString(R.string.agent_rewind_note)
            neutral(getString(R.string.agent_cancel))
            negative(R.string.agent_edit_rewind_no) {
                composer.clear()
                hub.runtime.resend(conversationId, messageId, text, attachments, project, approval)
            }
            positive(R.string.agent_edit_rewind_yes) {
                composer.clear()
                lifecycleScope.launch {
                    runCatching { hub.runtime.rewindAndResend(conversationId, messageId, text, attachments, project, approval) }
                        .onSuccess(::reportRewind)
                        .onFailure { e -> toastLong(getString(R.string.agent_rewind_failed, e.message ?: e.toString())) }
                }
            }
        }
    }

    /** Asks how far to go back to before the user's message [messageId]: the files only, or the conversation too. */
    private fun confirmRewind(messageId: String) {
        if (hub.runtime.isRunning(conversationId))
            return
        val changes = hub.runtime.rewindable(conversationId, messageId)
        val message = hub.store.get(conversationId)?.messages?.firstOrNull { it.id == messageId }
        if (changes.isEmpty() || message == null)
            return
        val listed = changes.take(REWIND_LISTED).joinToString("\n") { "· $it" } +
            if (changes.size > REWIND_LISTED) "\n" + getString(R.string.agent_rewind_more, changes.size - REWIND_LISTED) else ""
        showSheet {
            title = getString(R.string.agent_rewind_title)
            icon = R.drawable.ic_agent_rewind
            this.message = getString(R.string.agent_rewind_message, changes.size) + "\n\n" + listed
            note = getString(R.string.agent_rewind_note)
            neutral(getString(R.string.agent_cancel))
            negative(R.string.agent_rewind_files) { rewind(message, conversationToo = false) }
            positive(R.string.agent_rewind_both) { rewind(message, conversationToo = true) }
        }
    }

    private fun rewind(message: ChatMessage, conversationToo: Boolean) {
        lifecycleScope.launch {
            runCatching { hub.runtime.rewind(conversationId, message.id, conversationToo) }
                .onSuccess { result ->
                    // The conversation went back to before the message; it waits in the field to be sent again.
                    if (conversationToo)
                        composer.fill(message.text, message.parts.filterIsInstance<Part.Attachment>())
                    reportRewind(result)
                }
                .onFailure { e -> toastLong(getString(R.string.agent_rewind_failed, e.message ?: e.toString())) }
        }
    }

    private fun reportRewind(result: Rewind.Result) {
        if (result.failed.isEmpty())
            Toast.makeText(this, getString(R.string.agent_rewind_done, result.restored.size), Toast.LENGTH_SHORT).show()
        else
            toastLong(getString(R.string.agent_rewind_partly, result.restored.size, result.failed.joinToString()))
    }

    private fun toastLong(text: String) =
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    /** Puts the user's message [messageId] in the composer, to be sent again in place of it and what followed. */
    private fun startEditing(messageId: String) {
        if (hub.runtime.isRunning(conversationId))
            return
        val messages = hub.store.get(conversationId)?.messages ?: return
        val index = messages.indexOfFirst { it.id == messageId && it.role == Role.USER }
        if (index < 0)
            return
        val message = messages[index]
        composer.edit(
            messageId = messageId,
            text = message.parts.filterIsInstance<Part.Text>().joinToString("\n\n") { it.text },
            attachments = message.parts.filterIsInstance<Part.Attachment>(),
            project = History.projectAt(messages.take(index + 1)),
        )
        composer.focus(showKeyboard = true)
    }

    private fun showMenu() {
        val items = listOf(
            AgentMenu.Item(MENU_USAGE, R.string.agent_usage_title, R.drawable.ic_agent_usage),
            AgentMenu.Item(MENU_COMPACT, R.string.agent_compact, R.drawable.ic_round_article_24),
            AgentMenu.Item(MENU_SETTINGS, R.string.agent_settings, R.drawable.ic_round_settings_24),
            AgentMenu.Item(MENU_DELETE, R.string.agent_delete_conversation, R.drawable.ic_agent_delete, danger = true),
        )
        AgentMenu.show(binding.menu, items) { id ->
            when (id) {
                MENU_USAGE    -> showUsage()
                MENU_COMPACT  -> compact()
                MENU_SETTINGS -> startActivity(Intent(this, AgentSettingsActivity::class.java))
                MENU_DELETE   -> confirmDelete()
            }
        }
    }

    /** Summarizes the conversation on the user's asking, so later requests are shorter. */
    private fun compact() {
        if (hub.runtime.isRunning(conversationId) || hub.store.get(conversationId) == null)
            return
        Toast.makeText(this, R.string.agent_compacting, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            runCatching { hub.runtime.compactNow(conversationId) }
                .onSuccess { done -> if (!done) Toast.makeText(this@AgentChatActivity, R.string.agent_compact_nothing, Toast.LENGTH_SHORT).show() }
                .onFailure { e -> toastLong(getString(R.string.agent_compact_failed, hub.runtime.describe(e))) }
        }
    }

    private fun showUsage() {
        val usage = hub.store.get(conversationId)?.usage ?: return
        showSheet {
            title = getString(R.string.agent_usage_title)
            icon = R.drawable.ic_agent_sparkle
            message = getString(
                R.string.agent_usage_detail,
                AgentUi.tokens(usage.input), AgentUi.tokens(usage.cacheRead), AgentUi.tokens(usage.cacheWrite),
                AgentUi.tokens(usage.output), AgentUi.cacheHitRate(usage),
            )
            positive(R.string.agent_done)
        }
    }

    private fun confirmDelete() {
        val conversation = hub.store.get(conversationId) ?: return finish()
        showSheet {
            title = getString(R.string.agent_delete_conversation)
            icon = R.drawable.ic_agent_delete
            message = getString(R.string.agent_delete_conversation_message, conversation.title)
            negative(R.string.agent_cancel)
            positive(R.string.agent_delete) {
                hub.runtime.delete(conversationId)
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_CONVERSATION = "conversation"
        private const val EXTRA_PROJECT = "project"
        private const val EXTRA_TEXT = "text"
        private const val KEY_SESSION = "session"
        private const val KEY_PROJECT = "session_project"
        private const val KEY_APPROVAL = "session_approval"
        private const val KEY_PENDING_SAVE = "pending_save"
        private const val KEY_PLAN_COLLAPSED = "plan_collapsed"
        private const val LIVE_INTERVAL_MILLIS = 90L
        private const val MAX_PICK = 5
        /** How many changes the rewind sheet lists by name. */
        private const val REWIND_LISTED = 8
        private const val MENU_USAGE = 1
        private const val MENU_SETTINGS = 2
        private const val MENU_DELETE = 3
        private const val MENU_COMPACT = 4
        /** The share of the model's window from which the header shows how full it is. */
        private const val CONTEXT_SHOWN_FROM = 0.3f

        /** Opens [conversationId], or a new conversation when it is null. */
        fun intent(context: Context, conversationId: String?, project: String? = null, text: String? = null): Intent =
            Intent(context, AgentChatActivity::class.java)
                .putExtra(EXTRA_CONVERSATION, conversationId)
                .putExtra(EXTRA_PROJECT, project)
                .putExtra(EXTRA_TEXT, text)
    }
}
