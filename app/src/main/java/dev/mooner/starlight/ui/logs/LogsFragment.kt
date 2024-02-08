/*
 * LogsFragment.kt created by Minki Moon(mooner1022) on 8/5/23, 1:50 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.logs

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BasicGridItem
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.gridItems
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
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
import dev.mooner.starlight.utils.setCommonAttrs
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val logs = LogCollector.logs
                logs.takeIf { l -> l[0].hashCode() != lastHash }
                    ?.let { l ->
                        if (itemAdapter == null)
                            createAdapter(binding.recyclerViewLogs, l)
                        else
                            itemAdapter!!.set(l)
                    }

                if (autoScroll) {
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
        msgRegex   = cat.getString("message_regex")?.let(Pattern::compile)

        val logs = LogCollector.logs
        if (logs.isEmpty()) {
            binding.textViewNoLogsYet.visibility = View.VISIBLE
            binding.recyclerViewLogs.visibility = View.GONE
        } else {
            binding.textViewNoLogsYet.visibility = View.GONE
            binding.recyclerViewLogs.visibility = View.VISIBLE
        }

        binding.cardViewTypeConfig.setOnClickListener(this)
        binding.cardViewFilter.setOnClickListener(this)

        createAdapter(binding.recyclerViewLogs, logs)
        binding.recyclerViewLogs.post {
            itemAdapter?.setFilter(types, tags, msgRegex)
        }

        EventHandler.on(lifecycleScope, ::onLogCreated)
        setLastHash()

        //binding.root.layoutTransition = LayoutTransition()
        return binding.root
    }

    override fun onClick(v: View) {
        when(v.id) {
            R.id.cardViewTypeConfig ->
                showModeSelectDialog(requireActivity())
            R.id.cardViewFilter ->
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
        itemAdapter = LogsRecyclerViewAdapter().withData(data, type)
        recyclerView.init(itemAdapter!!)
    }

    private fun RecyclerView.init(adapter: RecyclerView.Adapter<*>) {
        //itemAnimator = FadeInUpAnimator()
        layoutManager = initLayoutManager(context)
        this.adapter = adapter
        visibility = View.VISIBLE
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
                set("auto_scroll", autoScroll)
                set("types", Session.json.encodeToString(types))
                set("tags", Session.json.encodeToString(tags))
                if (msgRegex != null)
                    set("message_regex", msgRegex!!.pattern())
                else
                    remove("message_regex")
            }
        }
    }

    context(LifecycleOwner)
    @SuppressLint("CheckResult")
    private fun showLogFilterConfigDialog(context: Context) {
        MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).noAutoDismiss().show {
            setCommonAttrs()
            title(res = R.string.log_filter_settings)

            val inflater = LayoutInflater.from(context as Activity)
            val binding = DialogLogFilterBinding.inflate(inflater)
            customView(view = binding.root, scrollable = true)

            binding.cardViewClearFilter.setOnClickListener {
                autoScroll = true
                types = emptyList()
                tags = emptyList()
                msgRegex = null
                _binding!!.recyclerViewLogs.post {
                    itemAdapter?.setFilter(types, tags, null)
                }
                GlobalConfig.edit {
                    category("logs").apply {
                        remove("auto_scroll")
                        remove("types")
                        remove("tags")
                        remove("message_regex")
                    }
                }
                dismiss()
            }

            binding.toggleAutoScroll.isChecked = autoScroll
            binding.chipGroupTags.apply {
                if (layoutTransition == null)
                    layoutTransition = LayoutTransition()
                layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
            }
            val logTypes = LogType.values().sortedBy { it.priority }
            for ((idx, type) in logTypes.withIndex())
                Chip(context)
                    .apply {
                        id = idx
                        text = type.name
                        isCheckable = true
                        isChecked = type in types
                        setChipBackgroundColorResource(R.color.chip_selector)
                    }
                    .let(binding.chipGroupTags::addView)
            binding.editTextTags.setText(tags.joinToString(","))
            binding.editTextMessage.setText(msgRegex?.pattern() ?: "")

            negativeButton(res = R.string.cancel, click = MaterialDialog::dismiss)
            positiveButton(res = R.string.ok) {
                autoScroll = binding.toggleAutoScroll.isChecked
                types = binding.chipGroupTags.checkedChipIds.map(logTypes::get)
                tags  = binding.editTextTags.text.toString()
                    .ifBlank { null }
                    ?.split(",")
                    ?.also { v ->
                        if (("" in v) || v.any(String::isBlank)) {
                            binding.editTextTags.apply {
                                error = "유효하지 않은 태그 값"
                                requestFocus()
                            }
                            return@positiveButton
                        }
                    }
                    ?.map(String::trim) ?: emptyList()
                msgRegex = binding.editTextMessage.text.toString()
                    .ifBlank { null }
                    ?.let { v ->
                        try {
                            Pattern.compile(v)
                        } catch (e: PatternSyntaxException) {
                            binding.editTextMessage.apply {
                                error = "유효하지 않은 정규식"
                                requestFocus()
                            }
                            return@positiveButton
                        }
                    }

                _binding!!.recyclerViewLogs.post {
                    itemAdapter?.setFilter(types, tags, msgRegex)
                }
                saveLogFilterConfig()
                it.dismiss()
            }
        }
    }

    context(LifecycleOwner)
    @SuppressLint("CheckResult")
    private fun showModeSelectDialog(context: Context) {
        MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            var type: LogItem.ViewType = itemAdapter?.viewType ?: LogItem.ViewType.NORMAL
            setCommonAttrs()

            title(text = "로그 표시 모드")
            
            val items = listOf(
                BasicGridItem(
                    iconRes = R.drawable.ic_round_projects_24,
                    title = getString(R.string.log_type_card),
                ),
                BasicGridItem(
                    iconRes = R.drawable.ic_round_format_list_bulleted_24,
                    title = getString(R.string.log_type_text),
                ),
            )
            gridItems(items) { _, idx, _ ->
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
            itemAdapter?.push(log, limit = 100)
            if (autoScroll && itemAdapter != null)
                binding.recyclerViewLogs.scrollToPosition(itemAdapter!!.itemCount - 1)
        }
    }
}