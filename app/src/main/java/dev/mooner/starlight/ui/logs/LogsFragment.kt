/*
 * LogsFragment.kt created by Minki Moon(mooner1022) on 8/5/23, 1:50 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.logs

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.DialogLogFilterBinding
import dev.mooner.starlight.databinding.FragmentLogsBinding
import dev.mooner.starlight.logging.LogCollector
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.ui.common.Sheet
import dev.mooner.starlight.ui.common.SheetGridItem
import dev.mooner.starlight.ui.common.showSheet
import dev.mooner.starlight.utils.dp
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class LogsFragment : Fragment(), OnClickListener {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!

    private var itemAdapter: LogsRecyclerViewAdapter? = null
    private var lastHash: Int? = null

    private var autoScroll  : Boolean = true
    private var types       : List<LogType> = emptyList()
    private var tags        : List<String> = emptyList()
    private var msgRegex    : Pattern? = null

    private val logFilter: LogFilter
        get() = LogFilter(types, tags, msgRegex)

    /** Keeps the empty state right as logs come, go and are filtered. */
    private val listObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = updateEmptyState()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = updateEmptyState()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = updateEmptyState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val logs = LogCollector.logs.toList()
                logs.takeIf { l -> l.isNotEmpty() && l[0].hashCode() != lastHash }
                    ?.let { l ->
                        if (itemAdapter == null)
                            createAdapter(binding.recyclerViewLogs, l)
                        else
                            itemAdapter!!.set(l)
                    }

                if (autoScroll && logs.isNotEmpty()) {
                    binding.recyclerViewLogs.post {
                        binding.recyclerViewLogs.smoothScrollToPosition(logs.size - 1)
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)

        val cat = GlobalConfig.category("logs")
        autoScroll = cat.getBoolean("auto_scroll", autoScroll)
        types      = cat.getString("types", "[]").let(Session.json::decodeFromString)
        tags       = cat.getString("tags", "[]").let(Session.json::decodeFromString)
        msgRegex   = cat.getString("message_regex")?.let { runCatching { LogFilter.messagePattern(it) }.getOrNull() }

        val logs = LogCollector.logs
        binding.clearFilter.setOnClickListener { clearFilter() }

        binding.logSettings.setOnClickListener(this)
        binding.logFilter.setOnClickListener(this)
        binding.logAutoscroll.apply {
            setOnCheckedChangeListener { _, isChecked ->
                autoScroll = isChecked
                GlobalConfig.edit {
                    category("logs").apply {
                        set("auto_scroll", isChecked)
                    }
                }
            }
            isChecked = autoScroll
        }

        createAdapter(binding.recyclerViewLogs, logs)
        itemAdapter?.setFilter(logFilter)
        updateFilterChip()
        updateEmptyState()

        EventHandler.on(lifecycleScope, ::onLogCreated)
        setLastHash()

        return binding.root
    }

    override fun onClick(v: View) {
        when(v.id) {
            R.id.log_settings ->
                showModeSelectDialog(requireActivity())
            R.id.log_filter ->
                showLogFilterConfigDialog(requireActivity())
        }
    }

    override fun onPause() {
        super.onPause()
        setLastHash()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setLastHash()
        itemAdapter = null
    }

    private fun createAdapter(recyclerView: RecyclerView, data: List<LogData>) {
        val type = GlobalConfig
            .category("logs")
            .getString("view_type")
            ?.let(LogItem.ViewType::valueOf)
            ?: LogItem.ViewType.TEXT
        itemAdapter = LogsRecyclerViewAdapter().withData(data, type).also {
            it.setFilter(logFilter)
            it.registerAdapterDataObserver(listObserver)
        }
        recyclerView.init(itemAdapter!!)
    }

    /** Shows on the filter chip that a filter is set, and how many of its conditions: a list cut short says why. */
    private fun updateFilterChip() {
        val binding = _binding ?: return
        val filter = logFilter
        binding.logFilter.text = if (filter.isActive)
            getString(R.string.logs_filter_active, filter.conditions)
        else
            getString(R.string.logs_filter)
        binding.logFilter.chipStrokeWidth = if (filter.isActive) dp(1.5f) else 0f
        binding.logFilter.setChipStrokeColorResource(R.color.text_gold)
    }

    /**
     * With nothing in the list, says why: there are no logs yet, or there are and the filter shows none of them,
     * with the button that clears it. A filter set days ago used to leave an empty card with no word of it.
     */
    private fun updateEmptyState() {
        val binding = _binding ?: return
        val adapter = itemAdapter ?: return
        val isEmpty = adapter.itemCount == 0
        binding.emptyState.isVisible = isEmpty
        binding.recyclerViewLogs.isVisible = !isEmpty
        if (!isEmpty)
            return
        val filtered = adapter.hidesLogs
        binding.textViewNoLogsYet.setText(if (filtered) R.string.logs_filter_no_match else R.string.widget_logs_empty)
        binding.clearFilter.isVisible = filtered
    }

    private fun clearFilter() {
        types = emptyList()
        tags = emptyList()
        msgRegex = null
        itemAdapter?.setFilter(logFilter)
        updateFilterChip()
        GlobalConfig.edit {
            category("logs").apply {
                remove("types")
                remove("tags")
                remove("message_regex")
            }
        }
    }

    private fun RecyclerView.init(adapter: RecyclerView.Adapter<*>) {
        layoutManager = initLayoutManager(context)
        this.adapter = adapter
    }

    private fun initLayoutManager(context: Context): RecyclerView.LayoutManager =
        LinearLayoutManager(context).apply {
            reverseLayout = true
            stackFromEnd = true
        }

    private fun setLastHash() {
        if (itemAdapter != null && itemAdapter!!.itemCount != 0)
            lastHash = itemAdapter?.getItems()?.get(0)?.hashCode()
    }

    private fun saveLogFilterConfig() {
        GlobalConfig.edit {
            category("logs").apply {
                set("types", Session.json.encodeToString(types))
                set("tags", Session.json.encodeToString(tags))
                if (msgRegex != null)
                    set("message_regex", msgRegex!!.pattern())
                else
                    remove("message_regex")
            }
        }
    }

    context(lifecycle: LifecycleOwner)
    @SuppressLint("CheckResult")
    private fun showLogFilterConfigDialog(context: Context) {
        val binding = DialogLogFilterBinding.inflate(LayoutInflater.from(context))
        lateinit var sheet: Sheet
        sheet = context.showSheet {
            lifecycleOwner = lifecycle
            title = getString(R.string.log_filter_settings)
            icon = dev.mooner.configdsl.R.drawable.round_filter_alt_24
            content(binding.root)

            binding.cardViewClearFilter.setOnClickListener {
                // The filter only: auto scroll has its own chip, which this used to switch behind its back.
                clearFilter()
                sheet.dismiss()
            }

            binding.chipGroupTags.apply {
                if (layoutTransition == null)
                    layoutTransition = LayoutTransition()
                layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
            }
            val logTypes = LogType.entries.sortedBy { it.priority }
            for ((idx, type) in logTypes.withIndex())
                Chip(context)
                    .apply {
                        id = idx
                        text = type.name
                        isCheckable = true
                        isChecked = type in types
                        chipStartPadding = dp(8f)
                        chipEndPadding = dp(8f)
                        setChipBackgroundColorResource(R.color.chip_selector)
                    }
                    .let(binding.chipGroupTags::addView)
            binding.editTextTags.setText(tags.joinToString(","))
            binding.editTextMessage.setText(msgRegex?.pattern() ?: "")

            negative(R.string.cancel)
            positive(R.string.ok, autoDismiss = false) {
                types = binding.chipGroupTags.checkedChipIds.map(logTypes::get)
                tags  = binding.editTextTags.text.toString()
                    .ifBlank { null }
                    ?.split(",")
                    ?.also { v ->
                        if (("" in v) || v.any(String::isBlank)) {
                            binding.editTextTags.apply {
                                error = getString(R.string.logs_filter_invalid_tag)
                                requestFocus()
                            }
                            return@positive
                        }
                    }
                    ?.map(String::trim) ?: emptyList()
                msgRegex = binding.editTextMessage.text.toString()
                    .ifBlank { null }
                    ?.let { v ->
                        try {
                            LogFilter.messagePattern(v)
                        } catch (_: PatternSyntaxException) {
                            binding.editTextMessage.apply {
                                error = getString(R.string.logs_filter_invalid_regex)
                                requestFocus()
                            }
                            return@positive
                        }
                    }

                itemAdapter?.setFilter(logFilter)
                updateFilterChip()
                saveLogFilterConfig()
                it.dismiss()
            }
        }
    }

    context(lifecycle: LifecycleOwner)
    @SuppressLint("CheckResult")
    private fun showModeSelectDialog(context: Context) {
        var type: LogItem.ViewType = itemAdapter?.viewType ?: LogItem.ViewType.NORMAL
        context.showSheet {
            lifecycleOwner = lifecycle
            title = getString(R.string.logs_view_mode_title)
            icon = R.drawable.ic_round_view_day_24

            val items = listOf(
                SheetGridItem(
                    icon = R.drawable.ic_round_projects_24,
                    title = getString(R.string.log_type_card),
                ),
                SheetGridItem(
                    icon = dev.mooner.configdsl.R.drawable.ic_round_format_list_bulleted_24,
                    title = getString(R.string.log_type_text),
                ),
            )
            grid(items, selected = if (type == LogItem.ViewType.NORMAL) 0 else 1, columns = 2) { _, idx ->
                type = when(idx) {
                    0 -> LogItem.ViewType.NORMAL
                    1 -> LogItem.ViewType.TEXT
                    else -> LogItem.ViewType.TEXT
                }
            }

            onDismiss {
                GlobalConfig.edit {
                    category("logs")
                        .setString("view_type", type.name)
                }
                itemAdapter?.apply {
                    setViewType(type)
                    notifyItemRangeChanged(0, itemCount)
                }
            }
        }
    }

    private fun onLogCreated(event: Events.Log.Create) {
        val log = event.log
        val showInternalLog = GlobalConfig
            .category("dev_mode_config")
            .getBoolean("show_internal_log", false)

        if (log.type == LogType.VERBOSE && !showInternalLog) return

        //mAdapter?.pushLog(log, LOGS_MAX_SIZE)
        // Avoid infinite-loop
        //println(itemAdapter)
        if (itemAdapter == null)
            return

        binding.recyclerViewLogs.post {
            itemAdapter?.push(log, limit = 100, capacity = LogCollector.bufferSize)
            if (autoScroll && itemAdapter != null)
                binding.recyclerViewLogs.scrollToPosition(itemAdapter!!.itemCount - 1)
        }
    }
}