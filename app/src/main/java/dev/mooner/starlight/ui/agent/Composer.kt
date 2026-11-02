package dev.mooner.starlight.ui.agent

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LifecycleCoroutineScope
import coil.load
import coil.transform.RoundedCornersTransformation
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.ApprovalPolicy
import dev.mooner.starlight.agent.model.AttachmentKind
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.databinding.ItemAgentAttachmentChipBinding
import dev.mooner.starlight.databinding.ViewAgentComposerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The message field with its attachments and send button, used by the AI tab and the chat. Attachments are copied
 * into the conversation's folder as soon as they are picked.
 */
class Composer(
    private val binding: ViewAgentComposerBinding,
    private val scope: LifecycleCoroutineScope,
    private val conversationId: () -> String,
    private val pickImages: ActivityResultLauncher<PickVisualMediaRequest>,
    private val pickFiles: ActivityResultLauncher<Array<String>>,
    /** Hosts the add sheet; null while the screen is going away. */
    private val activity: () -> AppCompatActivity?,
    /** Returns whether the message was sent; if not, it stays in the field. */
    private val onSend: (text: String, attachments: List<Part.Attachment>) -> Boolean,
    private val onStop: () -> Unit,
    /** The user changed [project] or [approval] in the add sheet. */
    private val onSessionChanged: () -> Unit = {},
) {

    private val context: Context = binding.root.context
    private val hub = AgentHub.get(context)
    private val attachments = mutableListOf<Part.Attachment>()
    private var importing = 0
    /** The project before an edit put the edited message's in place, restored when the edit is cancelled. */
    private var projectBeforeEdit: String? = null

    /** The user's message being rewritten: sending replaces it and everything after it. Null when writing anew. */
    var editingMessage: String? = null
        private set

    var isRunning: Boolean = false
        set(value) {
            field = value
            updateSend()
        }

    /** The project the conversation is about, shown as a chip above the field. */
    var project: String? = null
        set(value) {
            val changed = field != value
            field = value
            binding.projectChip.isVisible = value != null
            binding.projectName.text = value
            if (changed)
                onSessionChanged()
        }

    /** When the agent asks before running tools in this conversation; null follows the settings. */
    var approval: ApprovalPolicy? = null
        set(value) {
            val changed = field != value
            field = value
            if (changed)
                onSessionChanged()
        }

    var text: String
        get() = binding.input.text.toString()
        set(value) {
            binding.input.setText(value)
            binding.input.setSelection(value.length)
        }

    init {
        binding.input.doAfterTextChanged { updateSend() }
        binding.send.setOnClickListener {
            // While a run goes on, a written message joins it; an empty field stops it.
            if (isRunning && !canSend()) {
                onStop()
                return@setOnClickListener
            }
            if (!canSend())
                return@setOnClickListener
            if (onSend(text, attachments.toList()))
                clear()
        }
        binding.attach.setOnClickListener { showAddSheet() }
        binding.projectChip.setOnClickListener { activity()?.let { AddSheet.pickProject(it, this) } }
        binding.projectRemove.setOnClickListener { project = null }
        binding.editCancel.setOnClickListener { cancelEdit() }
        updateSend()
    }

    fun focus(showKeyboard: Boolean = false) {
        binding.input.requestFocus()
        if (showKeyboard)
            showKeyboardWhenFocused()
    }

    /**
     * Android ignores the request while another window has the focus, such as a menu or sheet that is still closing
     * after the user picked from it; then it waits for the focus to come back.
     */
    private fun showKeyboardWhenFocused() {
        val input = binding.input
        fun show() = context.getSystemService(InputMethodManager::class.java)?.showSoftInput(input, 0)
        if (input.hasWindowFocus()) {
            show()
            return
        }
        input.viewTreeObserver.addOnWindowFocusChangeListener(object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus)
                    return
                input.viewTreeObserver.removeOnWindowFocusChangeListener(this)
                show()
            }
        })
    }

    fun setHint(hint: CharSequence) {
        binding.input.hint = hint
    }

    fun clear() {
        binding.input.text = null
        attachments.clear()
        binding.attachments.removeAllViews()
        binding.attachmentsScroll.isVisible = false
        editingMessage = null
        projectBeforeEdit = null
        binding.editBar.isVisible = false
        updateSend()
    }

    /**
     * Puts the user's message [messageId] in the field to rewrite, with its attachments and [project], the project it
     * was sent with.
     */
    fun edit(messageId: String, text: String, attachments: List<Part.Attachment>, project: String?) {
        val previous = if (editingMessage == null) this.project else projectBeforeEdit
        clear()
        projectBeforeEdit = previous
        editingMessage = messageId
        binding.editBar.isVisible = true
        this.project = project
        this.text = text
        attachments.forEach(::addChip)
        updateSend()
    }

    /** Puts a message back in the field as a new one, such as one the user rewound the conversation to before. */
    fun fill(text: String, attachments: List<Part.Attachment>) {
        clear()
        this.text = text
        attachments.forEach(::addChip)
        updateSend()
    }

    /** Leaves the edit with an empty field and the project as it was before. */
    fun cancelEdit() {
        if (editingMessage == null)
            return
        val previous = projectBeforeEdit
        clear()
        project = previous
    }

    /** Takes the result of either picker. */
    fun onPicked(uris: List<Uri>) {
        if (uris.isEmpty())
            return
        val id = conversationId()
        importing += uris.size
        updateSend()
        scope.launch {
            for (uri in uris) {
                val attachment = runCatching {
                    withContext(Dispatchers.IO) { hub.attachments.import(context.contentResolver, uri, id) }
                }
                attachment.onSuccess(::addChip).onFailure { e ->
                    Toast.makeText(context, context.getString(R.string.agent_attach_failed, uri.lastPathSegment.orEmpty(), e.message ?: e.toString()), Toast.LENGTH_LONG).show()
                }
                importing--
                updateSend()
            }
        }
    }

    private fun addChip(attachment: Part.Attachment) {
        attachments += attachment
        val chip = ItemAgentAttachmentChipBinding.inflate(LayoutInflater.from(context), binding.attachments, true)
        chip.name.text = attachment.name
        bindThumbnail(chip, attachment)
        chip.remove.setOnClickListener {
            attachments.remove(attachment)
            binding.attachments.removeView(chip.root)
            binding.attachmentsScroll.isVisible = attachments.isNotEmpty()
            updateSend()
        }
        binding.attachmentsScroll.isVisible = true
    }

    private fun bindThumbnail(chip: ItemAgentAttachmentChipBinding, attachment: Part.Attachment) {
        if (attachment.kind == AttachmentKind.IMAGE) {
            val file = hub.attachments.file(attachment) ?: return
            chip.thumb.imageTintList = null
            chip.thumb.setPadding(0, 0, 0, 0)
            chip.thumb.load(file) { transformations(RoundedCornersTransformation(AgentUi.dp(context, 10).toFloat())) }
        } else {
            chip.thumb.setImageResource(if (attachment.kind == AttachmentKind.PDF || attachment.kind == AttachmentKind.TEXT) R.drawable.ic_round_article_24 else R.drawable.ic_agent_attach)
        }
    }

    private fun showAddSheet() {
        val host = activity() ?: return
        AddSheet.show(
            activity = host,
            composer = this,
            onPhotos = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onFiles = { pickFiles.launch(arrayOf("*/*")) },
        )
    }

    private fun canSend(): Boolean =
        importing == 0 && (text.isNotBlank() || attachments.isNotEmpty())

    private fun updateSend() {
        val stops = isRunning && !canSend()
        binding.send.setImageResource(if (stops) R.drawable.ic_agent_stop else R.drawable.ic_agent_send)
        binding.send.contentDescription = context.getString(if (stops) R.string.agent_stop else R.string.agent_send)
        val enabled = isRunning || canSend()
        binding.send.isEnabled = enabled
        binding.send.alpha = if (enabled) 1f else 0.4f
    }
}
