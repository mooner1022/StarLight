package dev.mooner.starlight.ui.agent

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.RunState
import dev.mooner.starlight.agent.runtime.AgentNotifier
import dev.mooner.starlight.agent.runtime.LiveRun
import dev.mooner.starlight.agent.store.ConversationSummary
import dev.mooner.starlight.databinding.FragmentAgentBinding
import dev.mooner.starlight.databinding.ItemAgentConversationBinding
import dev.mooner.starlight.ui.common.showSheet
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The AI tab: a place to start a request, see what is running, and go back to conversations. */
class AgentFragment : Fragment() {

    private var _binding: FragmentAgentBinding? = null
    private val binding get() = _binding!!
    private val hub by lazy { AgentHub.get(requireContext()) }

    private var composer: Composer? = null
    /** Attachments picked here go to the conversation the next message starts. */
    private var draftId: String? = null

    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { composer?.onPicked(it) }
    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { composer?.onPicked(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAgentBinding.inflate(inflater, container, false)
        draftId = savedInstanceState?.getString(KEY_DRAFT)
        binding.stars.density = 0.5f
        binding.stars.brightness = 0.85f

        composer = Composer(
            binding = binding.composer,
            scope = viewLifecycleOwner.lifecycleScope,
            conversationId = { draftId ?: hub.runtime.newConversationId().also { draftId = it } },
            pickImages = pickImages,
            pickFiles = pickFiles,
            activity = { activity as? androidx.appcompat.app.AppCompatActivity },
            onSend = ::start,
            onStop = {},
        )
        binding.model.setOnClickListener { showModelSheet() }
        binding.connect.setOnClickListener { openSettings() }
        binding.settings.setOnClickListener { openSettings() }
        binding.newChat.setOnClickListener { startActivity(AgentChatActivity.intent(requireContext(), null)) }
        addSuggestions()
        observe()
        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_DRAFT, draftId)
    }

    override fun onDestroyView() {
        composer = null
        _binding = null
        super.onDestroyView()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // The streamed text isn't shown here; redraw only when a run's phase changes.
                val phases = hub.runtime.live
                    .map { runs -> runs.mapValues { (_, run) -> run.copy(text = "", thinking = "") } }
                    .distinctUntilChanged()
                combine(hub.store.summaries, phases, hub.settings.version) { summaries, live, _ -> summaries to live }
                    .collect { (summaries, live) -> render(summaries, live) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHeader()
    }

    /** The main screen sets its header when the tab changes; keep it current while this tab shows. */
    private fun refreshHeader() {
        if (isResumed)
            (activity as? dev.mooner.starlight.MainActivity)?.onPageChanged(R.id.nav_agent)
    }

    private fun render(summaries: List<ConversationSummary>, live: Map<String, LiveRun>) {
        refreshHeader()
        val summary = AgentUi.modelSummary(requireContext(), hub)
        val ready = summary != null
        binding.model.isVisible = ready
        binding.model.text = summary
        binding.composer.root.isVisible = ready
        binding.connect.isVisible = !ready
        binding.heroTitle.setText(if (ready) R.string.agent_hero_title else R.string.agent_setup_title)
        binding.heroBody.setText(if (ready) R.string.agent_hero_body else R.string.agent_setup_body)
        binding.suggestionsScroll.isVisible = ready

        val running = summaries.filter { it.id in live }
        binding.runningTitle.isVisible = running.isNotEmpty()
        binding.running.isVisible = running.isNotEmpty()
        binding.running.removeAllViews()
        for (item in running)
            addRow(binding.running, item, live[item.id])

        val others = summaries.filter { it.id !in live }.take(MAX_LISTED)
        binding.conversations.removeAllViews()
        for (item in others)
            addRow(binding.conversations, item, null)
        binding.empty.isVisible = summaries.isEmpty()
    }

    private fun addRow(parent: ViewGroup, item: ConversationSummary, run: LiveRun?) {
        val context = requireContext()
        val row = ItemAgentConversationBinding.inflate(layoutInflater, parent, true)
        row.title.text = item.title
        row.preview.text = run?.let { AgentNotifier.phaseText(context, it) } ?: item.preview.ifEmpty { "…" }
        row.meta.text = listOfNotNull(AgentUi.relativeTime(context, item.updatedAt), item.project).joinToString(" · ")
        val (label, color) = when {
            run?.phase == LiveRun.Phase.APPROVAL || item.state == RunState.WAITING_APPROVAL -> R.string.agent_state_approval to R.color.agent_dusk
            run != null                                                                     -> R.string.agent_state_running to R.color.agent_meteor
            item.state == RunState.INTERRUPTED                                              -> R.string.agent_state_interrupted to R.color.agent_dusk
            item.state == RunState.ERROR                                                    -> R.string.agent_state_error to R.color.agent_error
            else                                                                            -> null to null
        }
        row.state.isVisible = label != null
        if (label != null && color != null) {
            row.state.setText(label)
            row.state.setTextColor(ContextCompat.getColor(context, color))
        }
        if (run != null)
            row.icon.imageTintList = ContextCompat.getColorStateList(context, R.color.agent_meteor)
        row.root.setOnClickListener { startActivity(AgentChatActivity.intent(context, item.id)) }
        row.root.setOnLongClickListener {
            showRowMenu(row.root, item)
            true
        }
    }

    private fun showRowMenu(anchor: View, item: ConversationSummary) {
        val items = listOf(
            AgentMenu.Item(MENU_RENAME, R.string.agent_rename, R.drawable.ic_agent_edit),
            AgentMenu.Item(MENU_DELETE, R.string.agent_delete_conversation, R.drawable.ic_agent_delete, danger = true),
        )
        AgentMenu.show(anchor, items) { id ->
            when (id) {
                MENU_RENAME -> RenameSheet.show(requireContext(), viewLifecycleOwner, item.id)
                MENU_DELETE -> confirmDelete(item)
            }
        }
    }

    private fun confirmDelete(item: ConversationSummary) {
        requireContext().showSheet {
            title = getString(R.string.agent_delete_conversation)
            icon = R.drawable.ic_agent_delete
            message = getString(R.string.agent_delete_conversation_message, item.title)
            lifecycleOwner = viewLifecycleOwner
            negative(R.string.agent_cancel)
            positive(R.string.agent_delete) { hub.runtime.delete(item.id) }
        }
    }

    private fun addSuggestions() {
        val context = requireContext()
        val suggestions = listOf(
            R.string.agent_suggest_new_bot to R.string.agent_suggest_new_bot_prompt,
            R.string.agent_suggest_fix to R.string.agent_suggest_fix_prompt,
            R.string.agent_suggest_test to R.string.agent_suggest_test_prompt,
            R.string.agent_suggest_explain to R.string.agent_suggest_explain_prompt,
        )
        for ((label, prompt) in suggestions)
            binding.suggestions.addView(TextView(context).apply {
                setText(label)
                setBackgroundResource(R.drawable.bg_agent_chip_outline)
                setTextColor(ContextCompat.getColor(context, R.color.agent_text))
                typeface = ResourcesCompat.getFont(context, R.font.wantedsans_medium)
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                minHeight = AgentUi.dp(context, 38)
                setPadding(AgentUi.dp(context, 14), 0, AgentUi.dp(context, 14), 0)
                layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = AgentUi.dp(context, 8) }
                // Only fills the box: sending is the user's call, since every request costs them.
                setOnClickListener {
                    composer?.text = getString(prompt)
                    composer?.focus(showKeyboard = true)
                }
            })
    }

    private fun start(text: String, attachments: List<Part.Attachment>): Boolean {
        if (hub.settings.activeProvider() == null) {
            openSettings()
            return false
        }
        val id = draftId ?: hub.runtime.newConversationId()
        draftId = null
        hub.runtime.send(id, text, attachments, composer?.project, composer?.approval)
        // The next chat started here begins without them again.
        composer?.project = null
        composer?.approval = null
        startActivity(AgentChatActivity.intent(requireContext(), id))
        return true
    }

    private fun showModelSheet() {
        val activity = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        ModelSheet.show(activity)
    }

    private fun openSettings() =
        startActivity(Intent(requireContext(), AgentSettingsActivity::class.java))

    private companion object {
        const val KEY_DRAFT = "draft"
        const val MAX_LISTED = 40
        const val MENU_RENAME = 1
        const val MENU_DELETE = 2
    }
}
