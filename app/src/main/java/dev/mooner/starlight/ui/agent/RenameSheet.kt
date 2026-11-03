package dev.mooner.starlight.ui.agent

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.runtime.Titles
import dev.mooner.starlight.databinding.SheetAgentRenameBinding
import dev.mooner.starlight.ui.common.Sheet
import dev.mooner.starlight.ui.common.showSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Renames a conversation. The user writes the name, or has the model suggest one from the conversation as it is now
 * and changes it if they like; either way it is theirs, and the model doesn't rename the conversation again.
 */
internal object RenameSheet {

    fun show(context: Context, owner: LifecycleOwner, conversationId: String) {
        val hub = AgentHub.get(context)
        val conversation = hub.store.get(conversationId) ?: return
        val binding = SheetAgentRenameBinding.inflate(LayoutInflater.from(context))
        val note = context.getString(R.string.agent_rename_note)
        binding.name.setText(conversation.title)
        // Asking costs a request, so only where there is something to name and a model to ask.
        binding.suggest.isVisible = hub.settings.activeProvider() != null && Titles.canName(conversation)

        var suggesting: Job? = null
        lateinit var sheet: Sheet

        fun save() {
            val name = binding.name.text.toString().trim()
            if (name.isEmpty())
                return
            if (name != conversation.title)
                hub.runtime.rename(conversationId, name)
            sheet.dismiss()
        }

        fun setBusy(busy: Boolean) {
            binding.suggest.isEnabled = !busy
            binding.suggest.alpha = if (busy) 0.5f else 1f
            binding.suggesting.isVisible = busy
        }

        sheet = context.showSheet {
            title = context.getString(R.string.agent_rename_title)
            icon = R.drawable.ic_agent_edit
            this.note = note
            lifecycleOwner = owner
            content(binding.root, scrollable = false)
            neutral(context.getString(R.string.agent_cancel))
            positive(R.string.agent_save, autoDismiss = false) { save() }
            onShow { showKeyboard(binding.name) }
            onDismiss { suggesting?.cancel() }
        }

        binding.name.doAfterTextChanged { sheet.setPositiveEnabled(!it.isNullOrBlank()) }
        // The keyboard's done key, or Enter on a hardware keyboard, which would otherwise move on to the suggest button.
        binding.name.setOnEditorActionListener { _, action, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (action == EditorInfo.IME_ACTION_DONE || (enter && event?.action == KeyEvent.ACTION_DOWN))
                save()
            action == EditorInfo.IME_ACTION_DONE || enter
        }
        binding.suggest.setOnClickListener {
            if (suggesting?.isActive == true)
                return@setOnClickListener
            setBusy(true)
            sheet.setNote(context.getString(R.string.agent_rename_suggesting))
            suggesting = owner.lifecycleScope.launch {
                try {
                    val title = hub.runtime.suggestTitle(conversationId)
                    if (title == null) {
                        sheet.setNote(context.getString(R.string.agent_rename_suggest_none), isError = true)
                    } else {
                        binding.name.setText(title)
                        binding.name.setSelection(title.length)
                        sheet.setNote(note)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    sheet.setNote(context.getString(R.string.agent_rename_suggest_failed, hub.runtime.describe(e)), isError = true)
                } finally {
                    setBusy(false)
                }
            }
        }
    }

    /** Focuses [field] with the name selected, and opens the keyboard once the sheet's window has the focus. */
    private fun showKeyboard(field: EditText) {
        field.requestFocus()
        field.selectAll()
        fun show() = field.context.getSystemService(InputMethodManager::class.java)?.showSoftInput(field, 0)
        if (field.hasWindowFocus()) {
            show()
            return
        }
        field.viewTreeObserver.addOnWindowFocusChangeListener(object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus)
                    return
                field.viewTreeObserver.removeOnWindowFocusChangeListener(this)
                show()
            }
        })
    }
}
