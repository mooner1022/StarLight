package dev.mooner.starlight.ui.agent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.PopupMenu
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
import dev.mooner.starlight.agent.model.Conversation
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.model.RunState
import dev.mooner.starlight.databinding.ActivityAgentChatBinding
import dev.mooner.starlight.ui.common.showSheet
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/** One conversation with the agent. Runs go on when this screen closes; it only shows them. */
class AgentChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgentChatBinding
    private lateinit var composer: Composer
    private lateinit var adapter: ChatAdapter
    private val hub by lazy { AgentHub.get(this) }

    private lateinit var conversationId: String
    private var project: String? = null

    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { composer.onPicked(it) }
    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { composer.onPicked(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgentChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        conversationId = savedInstanceState?.getString(EXTRA_CONVERSATION)
            ?: intent.getStringExtra(EXTRA_CONVERSATION)
            ?: hub.runtime.newConversationId()
        project = intent.getStringExtra(EXTRA_PROJECT)

        setUpWindow()
        binding.stars.density = 0.35f
        binding.stars.brightness = 0.8f
        binding.back.setOnClickListener { finish() }
        binding.titleArea.setOnClickListener { ModelSheet.show(this) }
        binding.menu.setOnClickListener { showMenu() }

        adapter = ChatAdapter(this, AgentUi.markdown(this)) { callId, allow, always ->
            hub.runtime.resolveApproval(conversationId, callId, allow, always)
        }
        binding.messages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messages.adapter = adapter
        binding.messages.itemAnimator = null

        composer = Composer(
            binding = binding.composer,
            scope = lifecycleScope,
            conversationId = { conversationId },
            pickImages = pickImages,
            pickFiles = pickFiles,
            onSend = ::send,
            onStop = { hub.runtime.stop(conversationId) },
        )
        intent.getStringExtra(EXTRA_TEXT)?.let { composer.text = it }
        if (project != null && hub.store.get(conversationId) == null)
            composer.setHint(getString(R.string.agent_project_prompt_hint, project))
        binding.resumeButton.setOnClickListener { hub.runtime.resume(conversationId) }

        observe()
        if (hub.store.get(conversationId) == null)
            composer.focus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_CONVERSATION, conversationId)
    }

    override fun onResume() {
        super.onResume()
        hub.runtime.visibleConversation = conversationId
        updateSubtitle()
    }

    override fun onPause() {
        if (hub.runtime.visibleConversation == conversationId)
            hub.runtime.visibleConversation = null
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
            insets
        }
    }

    @OptIn(FlowPreview::class)
    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val conversations = hub.store.changes.map { it[conversationId] ?: hub.store.get(conversationId) }
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

    private fun render(conversation: Conversation?, run: dev.mooner.starlight.agent.runtime.LiveRun?) {
        binding.title.text = conversation?.title ?: getString(R.string.agent_new_chat)
        val running = hub.runtime.isRunning(conversationId)
        composer.isRunning = running
        composer.setHint(getString(if (conversation == null) R.string.agent_composer_hint else R.string.agent_composer_hint_followup))
        if (conversation == null && project != null)
            composer.setHint(getString(R.string.agent_project_prompt_hint, project))
        val rows = conversation?.let { ChatRows.build(hub, it, run) }.orEmpty()
        val atBottom = !binding.messages.canScrollVertically(1)
        val grew = rows.size > adapter.itemCount
        adapter.submitList(rows) {
            if (rows.isNotEmpty() && (atBottom || grew))
                binding.messages.scrollToPosition(rows.size - 1)
        }
        updateResumeBar(conversation, running)
    }

    private fun updateResumeBar(conversation: Conversation?, running: Boolean) {
        val last = conversation?.messages?.lastOrNull { message -> message.parts.any { it !is Part.Notice } }
        val unanswered = last != null && (last.role != Role.ASSISTANT || last.toolCalls.isNotEmpty())
        val show = conversation != null && !running && unanswered
        binding.resumeBar.isVisible = show
        if (show)
            binding.resumeText.text = when (conversation!!.state) {
                RunState.ERROR       -> conversation.error ?: getString(R.string.agent_resume_error)
                RunState.INTERRUPTED -> getString(R.string.agent_resume_interrupted)
                else                 -> getString(R.string.agent_resume_paused)
            }
    }

    private fun updateSubtitle() {
        binding.subtitle.text = AgentUi.modelSummary(this, hub) ?: getString(R.string.agent_subtitle_setup)
    }

    private fun send(text: String, attachments: List<Part.Attachment>): Boolean {
        if (hub.settings.activeProvider() == null) {
            startActivity(Intent(this, AgentSettingsActivity::class.java))
            return false
        }
        hub.runtime.send(conversationId, text, attachments, project)
        return true
    }

    private fun showMenu() {
        PopupMenu(this, binding.menu).apply {
            menu.add(0, MENU_USAGE, 0, R.string.agent_usage_title)
            menu.add(0, MENU_SETTINGS, 1, R.string.agent_settings)
            menu.add(0, MENU_DELETE, 2, R.string.agent_delete_conversation)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_USAGE    -> showUsage()
                    MENU_SETTINGS -> startActivity(Intent(this@AgentChatActivity, AgentSettingsActivity::class.java))
                    MENU_DELETE   -> confirmDelete()
                }
                true
            }
        }.show()
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
        private const val LIVE_INTERVAL_MILLIS = 90L
        private const val MAX_PICK = 5
        private const val MENU_USAGE = 1
        private const val MENU_SETTINGS = 2
        private const val MENU_DELETE = 3

        /** Opens [conversationId], or a new conversation when it is null. */
        fun intent(context: Context, conversationId: String?, project: String? = null, text: String? = null): Intent =
            Intent(context, AgentChatActivity::class.java)
                .putExtra(EXTRA_CONVERSATION, conversationId)
                .putExtra(EXTRA_PROJECT, project)
                .putExtra(EXTRA_TEXT, text)
    }
}
