package dev.mooner.starlight.ui.agent

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LifecycleCoroutineScope
import coil.load
import coil.transform.RoundedCornersTransformation
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
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
    /** Returns whether the message was sent; if not, it stays in the field. */
    private val onSend: (text: String, attachments: List<Part.Attachment>) -> Boolean,
    private val onStop: () -> Unit,
) {

    private val context: Context = binding.root.context
    private val hub = AgentHub.get(context)
    private val attachments = mutableListOf<Part.Attachment>()
    private var importing = 0

    var isRunning: Boolean = false
        set(value) {
            field = value
            updateSend()
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
            if (isRunning) {
                onStop()
                return@setOnClickListener
            }
            if (!canSend())
                return@setOnClickListener
            if (onSend(text, attachments.toList()))
                clear()
        }
        binding.attach.setOnClickListener { view -> showAttachMenu(view) }
        updateSend()
    }

    fun focus() {
        binding.input.requestFocus()
    }

    fun setHint(hint: CharSequence) {
        binding.input.hint = hint
    }

    fun clear() {
        binding.input.text = null
        attachments.clear()
        binding.attachments.removeAllViews()
        binding.attachmentsScroll.isVisible = false
        updateSend()
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

    private fun showAttachMenu(anchor: View) {
        PopupMenu(context, anchor).apply {
            menu.add(0, MENU_IMAGE, 0, R.string.agent_attach_image)
            menu.add(0, MENU_FILE, 1, R.string.agent_attach_file)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_IMAGE -> pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    MENU_FILE  -> pickFiles.launch(arrayOf("*/*"))
                }
                true
            }
        }.show()
    }

    private fun canSend(): Boolean =
        importing == 0 && (text.isNotBlank() || attachments.isNotEmpty())

    private fun updateSend() {
        binding.send.setImageResource(if (isRunning) R.drawable.ic_agent_stop else R.drawable.ic_agent_send)
        binding.send.contentDescription = context.getString(if (isRunning) R.string.agent_stop else R.string.agent_send)
        val enabled = isRunning || canSend()
        binding.send.isEnabled = enabled
        binding.send.alpha = if (enabled) 1f else 0.4f
        binding.attach.isEnabled = !isRunning
    }

    private companion object {
        const val MENU_IMAGE = 1
        const val MENU_FILE = 2
    }
}
