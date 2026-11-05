/*
 * EventPickerList.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects.events

import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ItemApiLevelHeaderBinding
import dev.mooner.starlight.databinding.ItemEventAutoBinding
import dev.mooner.starlight.databinding.ItemEventExpanderBinding
import dev.mooner.starlight.databinding.ItemEventLabelBinding
import dev.mooner.starlight.databinding.ItemEventNoteBinding
import dev.mooner.starlight.databinding.ItemEventOptionBinding
import dev.mooner.starlight.databinding.ItemEventSourceBinding
import dev.mooner.starlight.databinding.ItemEventUnknownBinding
import dev.mooner.starlight.plugincore.project.event.AllowedEventEntries

/**
 * The events projects can receive, by source, checked as [entries] allows them, inside [container]. Checking an event
 * edits [entries] as little as it takes; [onChange] follows every change.
 */
internal class EventPickerList(
    private val container: LinearLayout,
    private val sources: List<EventSource>,
    entries: AllowedEventEntries,
    private val levelIds: Set<String>?,
    private val mode: Mode,
    private val onChange: () -> Unit,
) {

    enum class Mode {
        /** A project's settings: also events its API levels leave out, later events, and entries nothing matches. */
        FULL,
        /** A new project: the events its API levels have, which it gets functions for. */
        SIMPLE,
    }

    var entries: AllowedEventEntries = entries
        private set

    private val inflater = LayoutInflater.from(container.context)
    private val context = container.context

    private class Row(val item: EventItem, val binding: ItemEventOptionBinding)
    private class Section(
        val source: EventSource,
        val available: List<EventItem>,
        val binding: ItemEventSourceBinding,
        val rows: List<Row>,
        val auto: ItemEventAutoBinding?,
    )

    private val sections = mutableListOf<Section>()

    /** The sources whose events show: at first those with an event allowed, and StarLight's. */
    private val expanded: MutableSet<String> = sources
        .filter { source -> source.id == EventCatalog.STARLIGHT || source.events.any { entries.isAllowed(it.id) } }
        .mapTo(hashSetOf(), EventSource::id)

    /** The sources whose events outside the levels show. Both are kept while the list is built again. */
    private val showingUnavailable = mutableSetOf<String>()

    /** The events received now: allowed, and in the levels. */
    val received: List<EventItem>
        get() = sources.flatMap(EventSource::events).filter { isAvailable(it) && entries.isAllowed(it.id) }

    fun setEntries(entries: AllowedEventEntries) {
        this.entries = entries
        build()
    }

    fun build() {
        container.removeAllViews()
        sections.clear()
        for (source in sources) {
            val (available, unavailable) = source.events.partition(::isAvailable)
            if (available.isEmpty() && (mode == Mode.SIMPLE || unavailable.isEmpty()))
                continue
            sections += buildSection(source, available, unavailable)
        }
        if (mode == Mode.FULL)
            buildUnknown()
        refresh()
    }

    private fun buildSection(source: EventSource, available: List<EventItem>, unavailable: List<EventItem>): Section {
        val binding = ItemEventSourceBinding.inflate(inflater, container, true)
        binding.name.text = source.name
        binding.icon.loadEventSourceIcon(source.id)
        binding.selectAll.contentDescription = context.getString(R.string.event_picker_select_all, source.name)
        binding.selectAll.isVisible = available.isNotEmpty()
        binding.selectAll.setOnClickListener {
            val allowAll = available.any { !entries.isAllowed(it.id) }
            change { entries.setAllowed(available.map(EventItem::id), allowAll) }
        }
        fun showEvents(show: Boolean) {
            binding.card.isVisible = show
            binding.chevron.rotation = if (show) 180f else 0f
        }
        showEvents(source.id in expanded)
        binding.header.setOnClickListener {
            if (!expanded.remove(source.id))
                expanded += source.id
            showEvents(source.id in expanded)
        }

        // Labelled by level when the project's levels split them, as StarLight's do.
        val groups = available.groupBy(::levelLabel)
        val rows = groups.flatMap { (label, items) ->
            if (label != null && groups.size > 1)
                ItemEventLabelBinding.inflate(inflater, binding.rows, true).root.text = label
            items.map { item -> Row(item, inflateRow(binding.rows, item, isAvailable = true)) }
        }

        val unavailableRows = if (mode == Mode.FULL && unavailable.isNotEmpty()) {
            val expander = ItemEventExpanderBinding.inflate(inflater, binding.rows, true)
            expander.label.text = context.resources.getQuantityString(
                R.plurals.event_picker_unavailable, unavailable.size, unavailable.size)
            val rows = unavailable.map { item -> Row(item, inflateRow(binding.rows, item, isAvailable = false)) }
            fun showRows(show: Boolean) {
                rows.forEach { it.binding.root.isVisible = show }
                expander.chevron.rotation = if (show) 180f else 0f
            }
            showRows(source.id in showingUnavailable)
            expander.root.setOnClickListener {
                if (!showingUnavailable.remove(source.id))
                    showingUnavailable += source.id
                showRows(source.id in showingUnavailable)
            }
            rows
        } else
            emptyList()

        val auto = if (mode == Mode.FULL)
            ItemEventAutoBinding.inflate(inflater, binding.rows, true).apply {
                val canSet = entries.canSetAutomatic(source.id)
                description.text = if (canSet)
                    context.getString(R.string.event_picker_auto_description, source.name)
                else
                    context.getString(R.string.event_picker_auto_all)
                row.isEnabled = canSet
                toggle.isEnabled = canSet
                row.setOnClickListener {
                    change { entries.setAutomatic(source.id, !entries.isAutomatic(source.id)) }
                }
            }
        else
            null
        return Section(source, available, binding, rows + unavailableRows, auto)
    }

    private fun inflateRow(parent: LinearLayout, item: EventItem, isAvailable: Boolean): ItemEventOptionBinding =
        ItemEventOptionBinding.inflate(inflater, parent, true).apply {
            usage.text = item.event.usage
            description.text = item.event.description ?: item.event.name
            eventId.text = item.id
            if (isAvailable) {
                root.setOnClickListener {
                    change { entries.setAllowed(item.id, !entries.isAllowed(item.id)) }
                }
            } else {
                // Shown as the entries have it, so turning the level on later gives what was seen here.
                note.isVisible = item.levels.isNotEmpty()
                note.text = context.getString(R.string.event_picker_unavailable_reason,
                    item.levels.joinToString(" · ", transform = { it.name }))
                texts.alpha = DISABLED_ALPHA
                checkbox.alpha = DISABLED_ALPHA
                root.isEnabled = false
            }
        }

    private fun buildUnknown() {
        val unknown = entries.unknown
        if (unknown.isEmpty())
            return
        ItemApiLevelHeaderBinding.inflate(inflater, container, true).root.apply {
            text = context.getString(R.string.event_picker_unknown_title)
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        ItemEventNoteBinding.inflate(inflater, container, true).root.text =
            context.getString(R.string.event_picker_unknown_description)
        val card = ItemEventSourceBinding.inflate(inflater, container, true)
        card.header.isVisible = false
        card.card.isVisible = true
        for (entry in unknown)
            ItemEventUnknownBinding.inflate(inflater, card.rows, true).apply {
                this.entry.text = entry
                remove.contentDescription = context.getString(R.string.event_picker_remove, entry)
                remove.setOnClickListener {
                    entries.remove(entry)
                    build()
                    onChange()
                }
            }
    }

    private inline fun change(block: () -> Unit) {
        block()
        refresh()
        onChange()
    }

    private fun refresh() {
        for (section in sections) {
            for (row in section.rows)
                row.binding.checkbox.isChecked = entries.isAllowed(row.item.id)
            val count = section.available.count { entries.isAllowed(it.id) }
            with(section.binding) {
                this.count.text = "$count / ${section.available.size}"
                this.count.isVisible = section.available.isNotEmpty()
                // The count tells a partial choice; the app's checkbox style has no indeterminate state.
                selectAll.isChecked = count == section.available.size
            }
            section.auto?.toggle?.isChecked = entries.isAutomatic(section.source.id)
        }
    }

    private fun isAvailable(item: EventItem): Boolean =
        EventCatalog.isAvailable(levelIds, item)

    /** The first of the project's levels that has [item], or null for an event no level has, like plugins'. */
    private fun levelLabel(item: EventItem): String? =
        item.levels.firstOrNull { levelIds == null || it.id in levelIds }?.name

    private companion object {
        const val DISABLED_ALPHA = 0.45f
    }
}
