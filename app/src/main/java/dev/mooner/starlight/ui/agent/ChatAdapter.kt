package dev.mooner.starlight.ui.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.card.MaterialCardView
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.model.Approval
import dev.mooner.starlight.agent.model.AttachmentKind
import dev.mooner.starlight.agent.model.Card
import dev.mooner.starlight.agent.model.CardKind
import dev.mooner.starlight.agent.model.Conversation
import dev.mooner.starlight.agent.model.NoticeKind
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Questions
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.model.TurnUsage
import dev.mooner.starlight.agent.provider.RequestContent
import dev.mooner.starlight.agent.runtime.AgentNotifier
import dev.mooner.starlight.agent.runtime.AgentRuntime
import dev.mooner.starlight.agent.runtime.LiveRun
import dev.mooner.starlight.agent.runtime.ToolLabels
import dev.mooner.starlight.databinding.ItemAgentApprovalBinding
import dev.mooner.starlight.databinding.ItemAgentAttachmentChipBinding
import dev.mooner.starlight.databinding.ItemAgentFileCardBinding
import dev.mooner.starlight.databinding.ItemAgentLiveBinding
import dev.mooner.starlight.databinding.ItemAgentNoticeBinding
import dev.mooner.starlight.databinding.ItemAgentProjectCardBinding
import dev.mooner.starlight.databinding.ItemAgentQuestionBinding
import dev.mooner.starlight.databinding.ItemAgentQuestionOptionBinding
import dev.mooner.starlight.databinding.ItemAgentQueuedBinding
import dev.mooner.starlight.databinding.ItemAgentTextBinding
import dev.mooner.starlight.databinding.ItemAgentThinkingBinding
import dev.mooner.starlight.databinding.ItemAgentToolBinding
import dev.mooner.starlight.databinding.ItemAgentToolGroupBinding
import dev.mooner.starlight.databinding.ItemAgentUsageBinding
import dev.mooner.starlight.databinding.ItemAgentUserBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.editor.CodeEditorActivity
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.ui.debugroom.DebugRoomActivity
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import dev.mooner.starlight.ui.projects.config.ProjectConfigActivity
import dev.mooner.starlight.utils.startActivityWithExtra
import io.noties.markwon.Markwon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/** One line of the chat. */
sealed class ChatRow(val key: String) {
    data class User(
        val id: String,
        val text: String,
        val attachments: List<Part.Attachment>,
        val contexts: List<String>,
        val edited: Boolean = false,
    ) : ChatRow(id)
    data class Text(val id: String, val markdown: String) : ChatRow(id)
    data class Thinking(val id: String, val text: String) : ChatRow(id)
    data class Tool(val call: Part.ToolCall, val result: Part.ToolResult?, val running: Boolean) : ChatRow("tool:" + call.id)
    /** Heads the tool calls of one run; collapsed, only the latest call shows. */
    data class ToolGroup(val id: String, val count: Int, val expanded: Boolean) : ChatRow(id)
    data class FileCard(val callId: String, val card: Card) : ChatRow("card:$callId")
    data class ProjectCard(val callId: String, val card: Card) : ChatRow("card:$callId")
    /** A call waiting for the user, with what it would change in a file if its tool can tell. */
    data class ApprovalRequest(val call: Part.ToolCall, val description: String, val preview: String? = null) :
        ChatRow("approval:" + call.id)
    data class Notice(val id: String, val kind: NoticeKind, val text: String) : ChatRow(id)
    /** A question the agent waits on, with options to tap. */
    data class Question(val question: AgentRuntime.Question) : ChatRow("question:" + question.callId)
    /** A message the user sent during a run, waiting for the agent to read it. */
    data class Queued(val index: Int, val text: String) : ChatRow("queued:$index")
    data class Usage(val id: String, val usage: TurnUsage) : ChatRow(id)
    data class Live(val run: LiveRun) : ChatRow("live")
}

/** Turns a conversation, and the run in progress, into chat rows. */
internal object ChatRows {

    /** [expandedGroups] holds the [ChatRow.ToolGroup] ids the user opened; groups start collapsed. */
    fun build(hub: AgentHub, conversation: Conversation, live: LiveRun?, expandedGroups: Set<String> = emptySet()): List<ChatRow> {
        val rows = mutableListOf<ChatRow>()
        // Only the call the run waits on can be answered; one saved as pending before the app was killed can't.
        val awaited = hub.runtime.pendingApproval(conversation.id)?.first
        val question = hub.runtime.pendingQuestion(conversation.id)
        val results = conversation.messages
            .filter { it.role == Role.TOOL }
            .flatMap { it.parts.filterIsInstance<Part.ToolResult>() }
            .associateBy { it.callId }
        var runUsage = TurnUsage()
        var run = mutableListOf<ChatRow>()
        val lastAssistant = conversation.messages.lastOrNull { it.role == Role.ASSISTANT && it.toolCalls.isNotEmpty() }
        fun closeRun(index: Int) {
            rows += collapse(run, expandedGroups)
            run = mutableListOf()
            if (runUsage.totalInput > 0 || runUsage.output > 0)
                rows += ChatRow.Usage("usage:$index", runUsage)
            runUsage = TurnUsage()
        }
        conversation.messages.forEachIndexed { index, message ->
            when (message.role) {
                Role.USER -> {
                    val text = message.parts.filterIsInstance<Part.Text>().joinToString("\n\n") { it.text }
                    val attachments = message.parts.filterIsInstance<Part.Attachment>()
                    val contexts = message.parts.filterIsInstance<Part.Context>().map { it.label }
                        .filter { it !in Part.Context.HIDDEN }
                    message.compaction?.let { compaction ->
                        rows += ChatRow.Notice("compaction:" + message.id, NoticeKind.INFO,
                            hub.context.getString(R.string.agent_compacted, compaction.covers))
                        return@forEachIndexed
                    }
                    // What the app asks the model on its own, such as the reply it left out, stays in the same run.
                    if (text.isBlank() && attachments.isEmpty() && contexts.isEmpty())
                        return@forEachIndexed
                    closeRun(index)
                    rows += ChatRow.User(id = message.id, text = text, attachments = attachments, contexts = contexts, edited = message.edited)
                }
                Role.ASSISTANT -> {
                    message.usage?.let { runUsage += it }
                    message.parts.forEachIndexed { partIndex, part ->
                        val id = "${message.id}#$partIndex"
                        when (part) {
                            is Part.Text     -> if (part.text.isNotBlank()) run += ChatRow.Text(id, part.text)
                            is Part.Thinking -> if (part.text.isNotBlank()) run += ChatRow.Thinking(id, part.text)
                            is Part.ToolCall -> {
                                val result = results[part.id]
                                val card = result?.card?.takeIf { !result.isError }
                                // A hand-off shows as what it handed over, not as a tool call.
                                when (card?.kind) {
                                    CardKind.FILE    -> run += ChatRow.FileCard(part.id, card)
                                    CardKind.PROJECT -> run += ChatRow.ProjectCard(part.id, card)
                                    null             -> {
                                        val running = result == null && live != null && message === lastAssistant &&
                                            live.phase == LiveRun.Phase.RUNNING_TOOL && live.tool == part.name
                                        run += ChatRow.Tool(part, result, running)
                                    }
                                }
                                if (result == null && question?.callId == part.id)
                                    run += ChatRow.Question(question)
                                if (part.approval == Approval.PENDING && result == null && part.id == awaited) {
                                    val args = RequestContent.parseArguments(part.arguments)
                                    val description = args?.let { hub.tools.find(part.name)?.describe?.invoke(it) } ?: part.name
                                    run += ChatRow.ApprovalRequest(part, description, hub.runtime.approvalPreview(conversation.id, part.id))
                                }
                            }
                            is Part.Notice   -> run += ChatRow.Notice(id, part.kind, part.text)
                            else             -> Unit
                        }
                    }
                }
                Role.TOOL -> Unit
            }
        }
        if (live != null) {
            rows += collapse(run, expandedGroups)
            rows += ChatRow.Live(live)
        } else
            closeRun(conversation.messages.size)
        hub.runtime.queued(conversation.id).forEachIndexed { index, queued ->
            rows += ChatRow.Queued(index, queued.text.ifBlank { queued.attachments.joinToString { it.name } })
        }
        return rows
    }

    /**
     * Folds the finished tool calls of one run, and the reasoning from the first call on, under a header, leaving the
     * latest call in view. What the model said, approvals, notices and hand-offs stay visible.
     */
    internal fun collapse(run: List<ChatRow>, expandedGroups: Set<String>): List<ChatRow> {
        val tools = run.filterIsInstance<ChatRow.Tool>()
        if (tools.size < 2)
            return run
        val first = tools.first()
        val last = tools.last()
        val id = "group:" + first.call.id
        val expanded = id in expandedGroups
        val firstIndex = run.indexOf(first)
        val lastIndex = run.indexOf(last)
        val out = ArrayList<ChatRow>(run.size + 1)
        run.forEachIndexed { index, row ->
            if (row === first)
                out += ChatRow.ToolGroup(id, tools.size, expanded)
            val folded = !expanded && when (row) {
                is ChatRow.Tool     -> index in firstIndex until lastIndex && row.result != null
                is ChatRow.Thinking -> index > firstIndex
                else                -> false
            }
            if (!folded)
                out += row
        }
        return out
    }
}

/** What the chat rows ask of the screen that shows them. */
interface ChatActions {
    fun onApproval(callId: String, allow: Boolean, always: Boolean)
    fun onToggleGroup(id: String)
    /** Asks where to save [file], a copy of what [card] points at. */
    fun onSaveFile(card: Card, file: File)
    /** The user chose to rewrite their message [messageId] and go on from there. */
    fun onEdit(messageId: String)
    /** The user chose to put back what the agent changed from their message [messageId] on. */
    fun onRewind(messageId: String)
    /** The user answered the questions of the call [callId], one answer each, or skipped them (null). */
    fun onAnswer(callId: String, answers: List<String>?)
}

class ChatAdapter(
    private val context: Context,
    markwon: Markwon,
    private val conversationId: () -> String,
    private val actions: ChatActions,
) : ListAdapter<ChatRow, ChatAdapter.Holder>(DIFF) {

    private val hub = AgentHub.get(context)
    private val renderer = MarkdownRenderer(context, markwon)
    private val expanded = hashSetOf<String>()

    class Holder(val binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatRow.User            -> TYPE_USER
        is ChatRow.Text            -> TYPE_TEXT
        is ChatRow.Thinking        -> TYPE_THINKING
        is ChatRow.Tool            -> TYPE_TOOL
        is ChatRow.ToolGroup       -> TYPE_GROUP
        is ChatRow.FileCard        -> TYPE_FILE
        is ChatRow.ProjectCard     -> TYPE_PROJECT
        is ChatRow.ApprovalRequest -> TYPE_APPROVAL
        is ChatRow.Notice          -> TYPE_NOTICE
        is ChatRow.Usage           -> TYPE_USAGE
        is ChatRow.Live            -> TYPE_LIVE
        is ChatRow.Question        -> TYPE_QUESTION
        is ChatRow.Queued          -> TYPE_QUEUED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        val binding: ViewBinding = when (viewType) {
            TYPE_USER     -> ItemAgentUserBinding.inflate(inflater, parent, false)
            TYPE_TEXT     -> ItemAgentTextBinding.inflate(inflater, parent, false)
            TYPE_THINKING -> ItemAgentThinkingBinding.inflate(inflater, parent, false)
            TYPE_TOOL     -> ItemAgentToolBinding.inflate(inflater, parent, false)
            TYPE_GROUP    -> ItemAgentToolGroupBinding.inflate(inflater, parent, false)
            TYPE_FILE     -> ItemAgentFileCardBinding.inflate(inflater, parent, false).also {
                FilePreview.shrinkIcons(context, it.open, it.share, it.save)
            }
            TYPE_PROJECT  -> ItemAgentProjectCardBinding.inflate(inflater, parent, false).also {
                FilePreview.shrinkIcons(context, it.edit, it.debug, it.settings)
            }
            TYPE_APPROVAL -> ItemAgentApprovalBinding.inflate(inflater, parent, false)
            TYPE_QUESTION -> ItemAgentQuestionBinding.inflate(inflater, parent, false)
            TYPE_QUEUED -> ItemAgentQueuedBinding.inflate(inflater, parent, false)
            TYPE_NOTICE   -> ItemAgentNoticeBinding.inflate(inflater, parent, false)
            TYPE_USAGE    -> ItemAgentUsageBinding.inflate(inflater, parent, false)
            else          -> ItemAgentLiveBinding.inflate(inflater, parent, false)
        }
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        when (val row = getItem(position)) {
            is ChatRow.User            -> bindUser(holder.binding as ItemAgentUserBinding, row)
            is ChatRow.Text            -> bindText(holder.binding as ItemAgentTextBinding, row)
            is ChatRow.Thinking        -> bindThinking(holder.binding as ItemAgentThinkingBinding, row)
            is ChatRow.Tool            -> bindTool(holder.binding as ItemAgentToolBinding, row)
            is ChatRow.ToolGroup       -> bindGroup(holder.binding as ItemAgentToolGroupBinding, row)
            is ChatRow.FileCard        -> bindFile(holder.binding as ItemAgentFileCardBinding, row.card)
            is ChatRow.ProjectCard     -> bindProject(holder.binding as ItemAgentProjectCardBinding, row.card)
            is ChatRow.ApprovalRequest -> bindApproval(holder.binding as ItemAgentApprovalBinding, row)
            is ChatRow.Notice          -> bindNotice(holder.binding as ItemAgentNoticeBinding, row)
            is ChatRow.Usage           -> (holder.binding as ItemAgentUsageBinding).text.text = AgentUi.usageLine(context, row.usage)
            is ChatRow.Live            -> bindLive(holder.binding as ItemAgentLiveBinding, row)
            is ChatRow.Question        -> bindQuestion(holder.binding as ItemAgentQuestionBinding, row.question)
            is ChatRow.Queued          -> (holder.binding as ItemAgentQueuedBinding).text.text = row.text
        }
    }

    private fun bindUser(binding: ItemAgentUserBinding, row: ChatRow.User) {
        binding.text.isVisible = row.text.isNotBlank()
        binding.text.text = row.text
        binding.contexts.removeAllViews()
        binding.contexts.isVisible = row.contexts.isNotEmpty()
        for (label in row.contexts)
            binding.contexts.addView(TextView(context).apply {
                text = "✦ " + if (label == Part.Context.NO_PROJECT) context.getString(R.string.agent_context_no_project) else label
                setBackgroundResource(R.drawable.bg_agent_chip)
                setTextColor(ContextCompat.getColor(context, R.color.agent_accent))
                textSize = 12f
                typeface = ResourcesCompat.getFont(context, R.font.wantedsans_medium)
                setPadding(AgentUi.dp(context, 10), AgentUi.dp(context, 4), AgentUi.dp(context, 10), AgentUi.dp(context, 4))
            })
        binding.attachments.removeAllViews()
        binding.attachments.isVisible = row.attachments.isNotEmpty()
        for (attachment in row.attachments) {
            if (attachment.kind == AttachmentKind.IMAGE) {
                val image = ImageView(context).apply {
                    adjustViewBounds = true
                    maxWidth = AgentUi.dp(context, 220)
                    maxHeight = AgentUi.dp(context, 220)
                    layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { bottomMargin = AgentUi.dp(context, 4) }
                }
                hub.attachments.file(attachment)?.let { file ->
                    image.load(file) { transformations(RoundedCornersTransformation(AgentUi.dp(context, 14).toFloat())) }
                }
                binding.attachments.addView(image)
            } else {
                val chip = ItemAgentAttachmentChipBinding.inflate(LayoutInflater.from(context), binding.attachments, false)
                chip.name.text = attachment.name
                chip.remove.isVisible = false
                chip.thumb.setImageResource(R.drawable.ic_round_article_24)
                binding.attachments.addView(chip.root)
            }
        }
        binding.text.setOnLongClickListener { showMessageMenu(it, row.text, row.id); true }
        binding.attachments.setOnLongClickListener { showMessageMenu(it, row.text, row.id); true }
        binding.edited.isVisible = row.edited
    }

    private fun bindText(binding: ItemAgentTextBinding, row: ChatRow.Text) {
        renderer.render(binding.content, row.markdown) { showMessageMenu(binding.content, row.markdown, null) }
    }

    private fun bindThinking(binding: ItemAgentThinkingBinding, row: ChatRow.Thinking) {
        val isExpanded = row.key in expanded
        binding.text.text = row.text
        binding.text.isVisible = isExpanded
        binding.chevron.rotation = if (isExpanded) 180f else 0f
        binding.header.setOnClickListener {
            if (!expanded.add(row.key))
                expanded.remove(row.key)
            notifyItemChanged(currentList.indexOf(row))
        }
    }

    private fun bindTool(binding: ItemAgentToolBinding, row: ChatRow.Tool) {
        val call = row.call
        val result = row.result
        binding.icon.setImageResource(ToolLabels.icon(call.name))
        binding.label.text = ToolLabels.label(context, call.name)
        binding.detail.text = result?.summary ?: ToolLabels.target(call.arguments)
        val (statusIcon, statusColor) = when {
            call.approval == Approval.DENIED -> R.drawable.ic_agent_close to R.color.agent_dusk
            result == null                   -> null to null
            result.isError                   -> R.drawable.ic_agent_close to R.color.agent_error
            else                             -> R.drawable.ic_agent_check to R.color.agent_ok
        }
        // Only the call running now spins; calls still to run, or cut off by a stop or a crash, show no status.
        binding.progress.isVisible = row.running
        binding.status.isVisible = statusIcon != null
        if (statusIcon != null && statusColor != null) {
            binding.status.setImageResource(statusIcon)
            binding.status.imageTintList = ContextCompat.getColorStateList(context, statusColor)
        }
        val isExpanded = row.key in expanded
        binding.details.isVisible = isExpanded
        if (isExpanded) {
            // What the call changed says more than its arguments, which hold the whole file for a write.
            val diff = result?.diff
            binding.diffLabel.isVisible = diff != null
            binding.diffScroll.isVisible = diff != null
            binding.argumentsLabel.isVisible = diff == null
            binding.arguments.isVisible = diff == null
            if (diff != null)
                binding.diff.text = DiffText.render(context, diff)
            binding.arguments.text = prettyArguments(call.arguments).take(MAX_DETAIL)
            binding.outputLabel.isVisible = result != null
            binding.output.isVisible = result != null
            binding.output.text = result?.output?.take(MAX_DETAIL)
        }
        binding.row.setOnClickListener {
            if (!expanded.add(row.key))
                expanded.remove(row.key)
            notifyItemChanged(currentList.indexOf(row))
        }
    }

    private fun bindGroup(binding: ItemAgentToolGroupBinding, row: ChatRow.ToolGroup) {
        binding.label.text = context.resources.getQuantityString(R.plurals.agent_tools_used, row.count, row.count)
        binding.chevron.rotation = if (row.expanded) 180f else 0f
        binding.header.setOnClickListener { actions.onToggleGroup(row.id) }
    }

    private fun bindFile(binding: ItemAgentFileCardBinding, card: Card) {
        val file = hub.attachments.cardFile(conversationId(), card)
        binding.name.text = card.name
        binding.meta.text = listOfNotNull(
            card.project,
            card.path?.takeIf { it != card.name },
            Formatter.formatShortFileSize(context, file?.length() ?: card.size),
        ).joinToString(" · ")
        binding.caption.isVisible = !card.caption.isNullOrBlank()
        binding.caption.text = card.caption
        val isImage = card.mimeType?.startsWith("image/") == true
        binding.icon.setImageResource(if (isImage) R.drawable.ic_agent_image else R.drawable.ic_round_article_24)
        binding.extension.text = card.name.substringAfterLast('.', "").take(4).uppercase()
        binding.extension.isVisible = !isImage && binding.extension.text.isNotEmpty()
        binding.thumb.isVisible = isImage && file != null
        if (isImage && file != null)
            binding.thumb.load(file) { transformations(RoundedCornersTransformation(AgentUi.dp(context, 12).toFloat())) }
        binding.card.alpha = if (file == null) 0.55f else 1f
        val open = View.OnClickListener {
            withFile(card) { file ->
                val activity = context as? AppCompatActivity
                if (activity != null)
                    FilePreview.open(activity, card, file) { actions.onSaveFile(card, file) }
                else
                    FilePreview.openWith(context, card, file)
            }
        }
        binding.main.setOnClickListener(open)
        binding.open.setOnClickListener(open)
        binding.share.setOnClickListener { withFile(card) { FilePreview.share(context, card, it) } }
        binding.save.setOnClickListener { withFile(card) { actions.onSaveFile(card, it) } }
    }

    private fun bindProject(binding: ItemAgentProjectCardBinding, card: Card) {
        val name = card.project ?: card.name
        val project = Session.projectManager.getProjectByName(name)
        binding.name.text = name
        binding.caption.isVisible = !card.caption.isNullOrBlank()
        binding.caption.text = card.caption
        binding.card.alpha = if (project == null) 0.55f else 1f
        if (project == null) {
            binding.meta.setText(R.string.agent_card_project_missing)
            binding.status.isVisible = false
            binding.icon.setImageResource(R.drawable.ic_round_projects_24)
            binding.icon.imageTintList = ContextCompat.getColorStateList(context, R.color.agent_accent)
        } else {
            val (statusText, statusColor) = when {
                !project.isCompiled    -> R.string.project_status_not_compiled to R.color.orange
                project.info.isEnabled -> R.string.project_status_running to R.color.card_enabled
                else                   -> R.string.project_status_off to R.color.text_sub
            }
            val language = runCatching { project.getLanguage() }.getOrNull()
            binding.meta.text = listOfNotNull(language?.name, context.getString(statusText)).joinToString(" · ")
            binding.status.isVisible = true
            binding.status.backgroundTintList = ContextCompat.getColorStateList(context, statusColor)
            val icon: Any? = when (language?.id) {
                null       -> null
                "JS_RHINO" -> R.drawable.ic_js
                else       -> language.getIconFileOrNull()
            }
            binding.icon.imageTintList = if (icon == null) ContextCompat.getColorStateList(context, R.color.agent_accent) else null
            binding.icon.load(icon ?: R.drawable.ic_round_projects_24)
        }
        fun withProject(block: (Project) -> Unit) {
            val current = Session.projectManager.getProjectByName(name)
            if (current == null)
                toast(R.string.agent_card_project_missing)
            else
                block(current)
        }
        val edit = View.OnClickListener {
            withProject { project ->
                context.startActivityWithExtra(DefaultEditorActivity::class.java, mapOf(
                    CodeEditorActivity.KEY_BASE_DIRECTORY to project.directory.path,
                    CodeEditorActivity.KEY_PROJECT_NAME to project.info.name,
                    CodeEditorActivity.KEY_MAIN_FILE to project.info.mainScript,
                    "title" to project.info.name,
                ))
            }
        }
        binding.main.setOnClickListener(edit)
        binding.edit.setOnClickListener(edit)
        binding.debug.setOnClickListener {
            withProject { project ->
                if (!project.isCompiled)
                    toast(R.string.agent_card_not_compiled)
                else
                    context.startActivityWithExtra(DebugRoomActivity::class.java,
                        mapOf(DebugRoomActivity.EXTRA_PROJECT_NAME to project.info.name))
            }
        }
        binding.settings.setOnClickListener {
            withProject { project ->
                context.startActivityWithExtra(ProjectConfigActivity::class.java, mapOf("projectName" to project.info.name))
            }
        }
    }

    private fun bindApproval(binding: ItemAgentApprovalBinding, row: ChatRow.ApprovalRequest) {
        binding.icon.setImageResource(ToolLabels.icon(row.call.name))
        binding.description.text = row.description
        val preview = row.preview
        binding.previewScroll.isVisible = preview != null
        if (preview != null) {
            binding.preview.text = DiffText.render(context, preview)
            binding.preview.maxLines = PREVIEW_LINES
            // A long change opens in full on a tap.
            binding.preview.setOnClickListener {
                binding.preview.maxLines = if (binding.preview.maxLines == PREVIEW_LINES) Int.MAX_VALUE else PREVIEW_LINES
            }
        }
        binding.allow.setOnClickListener { actions.onApproval(row.call.id, allow = true, always = false) }
        binding.deny.setOnClickListener { actions.onApproval(row.call.id, allow = false, always = false) }
        binding.always.setOnClickListener { actions.onApproval(row.call.id, allow = true, always = true) }
    }

    /** Where the user is in answering a question: the step on screen, and what each step has picked or written. */
    private class QuestionState(size: Int) {
        var step = 0
        val picked = Array(size) { linkedSetOf<Int>() }
        val writing = BooleanArray(size)
        val written = Array(size) { "" }

        fun answer(item: Questions.Item, index: Int): String? {
            val chosen = picked[index].sorted().map { item.options[it].label }
            val own = written[index].trim().takeIf { writing[index] && it.isNotEmpty() }
            return (chosen + listOfNotNull(own)).joinToString(", ").takeIf(String::isNotEmpty)
        }
    }

    private val questionStates = HashMap<String, QuestionState>()

    private fun bindQuestion(binding: ItemAgentQuestionBinding, question: AgentRuntime.Question) {
        val items = question.questions.items
        val state = questionStates.getOrPut(question.callId) { QuestionState(items.size) }
        val index = state.step.coerceIn(0, items.lastIndex)
        val item = items[index]
        val last = index == items.lastIndex
        fun refresh() = notifyItemChanged(currentList.indexOfFirst { it is ChatRow.Question && it.question.callId == question.callId })
        fun advance() {
            if (last) {
                val answers = items.indices.map { state.answer(items[it], it).orEmpty() }
                questionStates.remove(question.callId)
                actions.onAnswer(question.callId, answers)
            } else {
                state.step = index + 1
                refresh()
            }
        }

        binding.step.text = listOfNotNull(
            if (items.size > 1) "${index + 1}/${items.size}" else null,
            item.header,
        ).joinToString(" · ")
        binding.step.isVisible = binding.step.text.isNotEmpty()
        binding.question.text = item.question
        binding.multipleHint.isVisible = item.multiple

        // The options, then "write my own", as selection cards.
        binding.options.removeAllViews()
        val inflater = LayoutInflater.from(context)
        item.options.forEachIndexed { option, choice ->
            val card = ItemAgentQuestionOptionBinding.inflate(inflater, binding.options, true)
            card.label.text = choice.label
            card.description.isVisible = choice.description != null
            card.description.text = choice.description
            card.root.highlight(option in state.picked[index])
            card.root.setOnClickListener {
                if (item.multiple) {
                    if (!state.picked[index].remove(option))
                        state.picked[index] += option
                    refresh()
                } else {
                    state.picked[index].clear()
                    state.picked[index] += option
                    state.writing[index] = false
                    advance()
                }
            }
        }
        val own = ItemAgentQuestionOptionBinding.inflate(inflater, binding.options, true)
        own.label.setText(R.string.agent_question_custom)
        own.root.highlight(state.writing[index])
        own.root.setOnClickListener {
            state.writing[index] = !state.writing[index]
            // Writing one's own answer replaces the pick of a single choice.
            if (state.writing[index] && !item.multiple)
                state.picked[index].clear()
            refresh()
        }

        (binding.custom.tag as? TextWatcher)?.let(binding.custom::removeTextChangedListener)
        binding.custom.isVisible = state.writing[index]
        binding.custom.setText(state.written[index])
        fun updateNext() {
            val ready = state.answer(item, index) != null
            binding.next.isEnabled = ready
            binding.next.alpha = if (ready) 1f else 0.4f
        }
        val watcher = binding.custom.doAfterTextChanged {
            state.written[index] = it?.toString().orEmpty()
            updateNext()
        }
        binding.custom.tag = watcher
        if (state.writing[index] && binding.custom.text.isEmpty())
            binding.custom.post {
                binding.custom.requestFocus()
                context.getSystemService(InputMethodManager::class.java)?.showSoftInput(binding.custom, 0)
            }

        // A single choice moves on by itself; several, or one's own words, need the button.
        binding.back.isVisible = index > 0
        binding.next.isVisible = item.multiple || state.writing[index] || index < state.picked.lastIndex && state.picked[index].isNotEmpty()
        binding.next.setText(if (last) R.string.agent_question_send else R.string.agent_question_next)
        binding.footer.isVisible = binding.back.isVisible || binding.next.isVisible
        updateNext()
        binding.back.setOnClickListener {
            state.step = index - 1
            refresh()
        }
        binding.next.setOnClickListener { if (state.answer(item, index) != null) advance() }
        binding.skip.setOnClickListener {
            questionStates.remove(question.callId)
            actions.onAnswer(question.callId, null)
        }
    }

    /** The selection look of the app's option sheets: a gold outline and tint when picked. */
    private fun MaterialCardView.highlight(selected: Boolean) {
        if (selected) {
            strokeWidth = AgentUi.dp(context, 2)
            strokeColor = context.getColor(R.color.main_bright)
            setCardBackgroundColor(SELECTED_OPTION_COLOR.toColorInt())
        } else {
            strokeWidth = 0
            setCardBackgroundColor(context.getColor(dev.mooner.configdsl.R.color.background_selection_card))
        }
    }

    private fun bindNotice(binding: ItemAgentNoticeBinding, row: ChatRow.Notice) {
        val isError = row.kind == NoticeKind.ERROR || row.kind == NoticeKind.REFUSAL
        binding.text.text = row.text
        binding.text.setBackgroundResource(if (isError) R.drawable.bg_agent_notice_error else R.drawable.bg_agent_notice)
        binding.text.setTextColor(ContextCompat.getColor(context, if (isError) R.color.agent_error else R.color.agent_text_muted))
    }

    private fun bindLive(binding: ItemAgentLiveBinding, row: ChatRow.Live) {
        val run = row.run
        binding.phase.text = run.error?.takeIf { run.phase == LiveRun.Phase.RETRYING }
            ?.let { "${AgentNotifier.phaseText(context, run)}\n$it" }
            ?: AgentNotifier.phaseText(context, run)
        binding.thinking.isVisible = run.text.isEmpty() && run.thinking.isNotBlank()
        binding.thinking.text = run.thinking.takeLast(400).trim()
        binding.content.isVisible = run.text.isNotEmpty()
        if (run.text.isNotEmpty())
            renderer.render(binding.content, run.text)
        // A slow twinkle while the run goes on, started once per view.
        if (binding.spark.tag == null)
            binding.spark.tag = android.animation.ObjectAnimator.ofFloat(binding.spark, View.ALPHA, 1f, 0.3f).apply {
                duration = 900
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                start()
            }
    }

    private fun withFile(card: Card, block: (File) -> Unit) {
        val file = hub.attachments.cardFile(conversationId(), card)
        if (file == null)
            toast(R.string.agent_card_missing)
        else
            block(file)
    }

    private fun prettyArguments(arguments: String): String =
        runCatching { PRETTY.encodeToString(JsonObject.serializer(), AgentJson.parseToJsonElement(arguments) as JsonObject) }
            .getOrDefault(arguments)

    /** A message's long-press menu: copying it, and for the user's own messages, rewriting it to go on from there. */
    private fun showMessageMenu(anchor: View, text: String, userMessageId: String?) {
        val items = buildList {
            if (text.isNotBlank())
                add(AgentMenu.Item(MENU_COPY, R.string.agent_message_copy, R.drawable.ic_agent_copy))
            // Rewriting cuts the history, which a run going on still writes to.
            if (userMessageId != null && !hub.runtime.isRunning(conversationId())) {
                add(AgentMenu.Item(MENU_EDIT, R.string.agent_message_edit, R.drawable.ic_agent_edit))
                if (hub.runtime.rewindable(conversationId(), userMessageId).isNotEmpty())
                    add(AgentMenu.Item(MENU_REWIND, R.string.agent_message_rewind, R.drawable.ic_agent_rewind))
            }
        }
        if (items.isEmpty())
            return
        AgentMenu.show(anchor, items) { id ->
            when (id) {
                MENU_COPY -> copy(text)
                MENU_EDIT -> userMessageId?.let(actions::onEdit)
                MENU_REWIND -> userMessageId?.let(actions::onRewind)
            }
        }
    }

    private fun copy(text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("StarLight AI", text))
        toast(R.string.agent_copied)
    }

    private fun toast(@StringRes text: Int) =
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    private companion object {
        const val TYPE_USER = 0
        const val TYPE_TEXT = 1
        const val TYPE_THINKING = 2
        const val TYPE_TOOL = 3
        const val TYPE_APPROVAL = 4
        const val TYPE_NOTICE = 5
        const val TYPE_USAGE = 6
        const val TYPE_LIVE = 7
        const val TYPE_GROUP = 8
        const val TYPE_FILE = 9
        const val TYPE_PROJECT = 10
        const val TYPE_QUESTION = 11
        const val TYPE_QUEUED = 12
        const val MAX_DETAIL = 6_000
        const val MENU_COPY = 1
        const val MENU_EDIT = 2
        const val MENU_REWIND = 3
        /** Lines of a diff an approval card shows before it is tapped open. */
        const val PREVIEW_LINES = 14
        /** The tint of a picked option, as the app's option sheets have it. */
        const val SELECTED_OPTION_COLOR = "#50AD977A"

        val PRETTY = Json { prettyPrint = true }

        val DIFF = object : DiffUtil.ItemCallback<ChatRow>() {
            override fun areItemsTheSame(oldItem: ChatRow, newItem: ChatRow) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: ChatRow, newItem: ChatRow) = oldItem == newItem
        }
    }
}
