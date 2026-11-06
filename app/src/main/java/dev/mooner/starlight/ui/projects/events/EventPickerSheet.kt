/*
 * EventPickerSheet.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects.events

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.MultiAutoCompleteTextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ItemEventNoteBinding
import dev.mooner.starlight.databinding.SheetEventPickerBinding
import dev.mooner.starlight.plugincore.project.event.AllowedEventEntries
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.ui.common.showSheet

/**
 * Lets the user choose the events a project receives, starting from its [entries] and with the API levels [levelIds]
 * it has; [onConfirm] gets the entries chosen. Events are checked in a list, and the entries can be edited as text.
 */
internal fun Context.showEventPicker(
    entries: Collection<String>,
    levelIds: Set<String>?,
    onConfirm: (entries: List<String>) -> Unit,
) {
    val inflater = LayoutInflater.from(this)
    val binding = SheetEventPickerBinding.inflate(inflater)
    val sources = EventCatalog.load()
    val items = sources.flatMap(EventSource::events).associateBy(EventItem::id)
    val probe = AllowedEventEntries(emptyList())
    var isEditingText = false

    fun updateSummary(received: Int) {
        binding.summary.text = if (received == 0)
            getString(R.string.event_picker_summary_none)
        else
            resources.getQuantityString(R.plurals.event_picker_summary, received, received)
        binding.summary.setTextColor(getColor(if (received == 0) R.color.status_warning else R.color.text))
    }

    lateinit var list: EventPickerList
    list = EventPickerList(binding.list, sources, AllowedEventEntries(entries), levelIds, EventPickerList.Mode.FULL) {
        updateSummary(list.received.size)
    }
    list.build()
    updateSummary(list.received.size)

    fun textEntries(): AllowedEventEntries =
        AllowedEventEntries(binding.editor.text.lines())

    fun isAvailable(id: String): Boolean =
        items[id]?.let { EventCatalog.isAvailable(levelIds, it) } == true

    // What each line does, as the user types.
    fun updateStatus() {
        binding.status.removeAllViews()
        val typed = textEntries()
        for (entry in typed.entries) {
            val matches = probe.matches(entry)
            val status = when {
                matches.isEmpty() -> getString(R.string.event_picker_raw_unknown)
                entry.startsWith(ProjectEventManager.EXCLUDE_PREFIX) -> {
                    // What the other entries would allow that this one keeps out.
                    val takenOut = AllowedEventEntries(typed.entries - entry).allowed.count { it in matches }
                    if (takenOut == 0)
                        getString(R.string.event_picker_raw_excludes_nothing)
                    else
                        getString(R.string.event_picker_raw_excludes, takenOut)
                }
                else -> {
                    val single = matches.singleOrNull()?.takeIf { it == entry }?.let(items::get)
                    val text = single?.event?.usage
                        ?: resources.getQuantityString(R.plurals.event_picker_raw_matches, matches.size, matches.size)
                    if (matches.none(::isAvailable))
                        text + " · " + getString(R.string.event_picker_raw_outside_levels)
                    else
                        text
                }
            }
            ItemEventNoteBinding.inflate(inflater, binding.status, true).root.apply {
                text = "$entry — $status"
                if (matches.isEmpty())
                    setTextColor(getColor(R.color.status_warning))
            }
        }
        updateSummary(items.values.count { typed.isAllowed(it.id) && isAvailable(it.id) })
    }

    val suggestions = buildList {
        for (source in sources) {
            add(AllowedEventEntries.wildcardOf(source.id))
            source.events.mapTo(this) { it.id }
        }
    }
    binding.editor.setAdapter(ArrayAdapter(this, R.layout.item_event_suggestion, suggestions))
    binding.editor.setTokenizer(LineTokenizer)
    binding.editor.doAfterTextChanged { if (isEditingText) updateStatus() }

    showSheet {
        title = getString(R.string.event_picker_title)
        icon = R.drawable.ic_round_notifications_24
        message = getString(R.string.event_picker_message)
        content(binding.root)
        neutral(getString(R.string.event_picker_edit_ids), autoDismiss = false) { sheet ->
            isEditingText = !isEditingText
            if (isEditingText) {
                // Its text listener fills in what each line matches.
                binding.editor.setText(list.entries.entries.joinToString("\n"))
            } else {
                list.setEntries(textEntries())
                updateSummary(list.received.size)
            }
            binding.list.isVisible = !isEditingText
            binding.raw.isVisible = isEditingText
            sheet.setNeutralText(getString(
                if (isEditingText) R.string.event_picker_back_to_list else R.string.event_picker_edit_ids))
        }
        negative(R.string.cancel)
        positive(R.string.ok) {
            onConfirm((if (isEditingText) textEntries() else list.entries).entries)
        }
    }
}

/** Completes the line being typed, after a `!` that starts an exclusion. */
private object LineTokenizer : MultiAutoCompleteTextView.Tokenizer {

    override fun findTokenStart(text: CharSequence, cursor: Int): Int {
        var start = cursor
        while (start > 0 && text[start - 1] != '\n')
            start--
        while (start < cursor && (text[start] == '!' || text[start] == ' '))
            start++
        return start
    }

    override fun findTokenEnd(text: CharSequence, cursor: Int): Int {
        var end = cursor
        while (end < text.length && text[end] != '\n')
            end++
        return end
    }

    override fun terminateToken(text: CharSequence): CharSequence =
        text
}
