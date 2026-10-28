/*
 * EditorLogsFragment.kt created by Minki Moon(mooner1022) on 12/16/23, 1:42 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.editor.drawer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentEditorLogsBinding
import dev.mooner.starlight.databinding.ItemEditorLogBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.TimeUtils
import dev.mooner.starlight.utils.showConfirmDialog
import java.util.*
import kotlin.properties.Delegates.notNull

class EditorLogsFragment: Fragment() {

    private var _binding: FragmentEditorLogsBinding? = null
    val binding get() = _binding!!

    private var project: Project by notNull()
    private var listenerId: Int? = null
    private val logsAdapter = EditorLogsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectId = arguments?.getString(ARG_PROJECT_ID)
            ?.let(UUID::fromString)
            ?: throw IllegalArgumentException("Failed to retrieve project id")

        project = Session.projectManager.getProjects().find { it.info.id == projectId }
            ?: throw IllegalArgumentException("Unable to find project with id: $projectId")
    }

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater =
        editorThemedInflater(super.onGetLayoutInflater(savedInstanceState))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorLogsBinding.inflate(inflater, container, false)

        val logger = project.logger
        logsAdapter.set(synchronized(logger.logs) { logger.logs.toList() })
        binding.rvEditorLogs.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = logsAdapter
            itemAnimator = null
        }
        updateSummary()

        listenerId = logger.addOnLogCreateListener { log ->
            if (log.type == LogType.DEBUG)
                return@addOnLogCreateListener
            val view = _binding?.rvEditorLogs ?: return@addOnLogCreateListener
            view.post {
                val binding = _binding ?: return@post
                val atTop = !binding.rvEditorLogs.canScrollVertically(-1)
                logsAdapter.push(log)
                updateSummary()
                if (atTop)
                    binding.rvEditorLogs.scrollToPosition(0)
            }
        }

        TooltipCompat.setTooltipText(binding.buttonClearLogs, binding.buttonClearLogs.contentDescription)
        binding.buttonClearLogs.setOnClickListener {
            showConfirmDialog(
                context = requireContext(),
                title = getString(R.string.editor_clear_logs),
                message = translate {
                    Locale.ENGLISH { "All logs of ${project.info.name} will be deleted." }
                    Locale.KOREAN  { "${project.info.name}의 모든 로그가 삭제돼요." }
                },
                onDismiss = { confirm ->
                    if (!confirm)
                        return@showConfirmDialog
                    logger.clear()
                    logsAdapter.set(emptyList())
                    updateSummary()
                }
            )
        }

        return binding.root
    }

    private fun updateSummary() {
        val binding = _binding ?: return
        val count = logsAdapter.itemCount
        binding.textLogsSummary.text = getString(R.string.editor_logs_summary, count)
        binding.textNoLogs.isVisible = count == 0
        binding.buttonClearLogs.isEnabled = count > 0
        binding.buttonClearLogs.alpha = if (count > 0) 1f else 0.4f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerId?.let(project.logger::removeOnLogCreateListener)
        _binding = null
    }

    private class EditorLogsAdapter: RecyclerView.Adapter<EditorLogsAdapter.ViewHolder>() {

        private val logs: MutableList<LogData> = arrayListOf()

        @Suppress("NotifyDataSetChanged")
        fun set(list: List<LogData>) {
            logs.clear()
            logs += list.asReversed()
            notifyDataSetChanged()
        }

        fun push(log: LogData) {
            logs.add(0, log)
            notifyItemInserted(0)
        }

        override fun getItemCount(): Int = logs.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemEditorLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val log = logs[position]
            val context = holder.binding.root.context
            val color = context.getColor(colorOf(log.type))
            holder.binding.apply {
                viewLevel.backgroundTintList = ColorStateList.valueOf(color)
                textLogMeta.text = listOfNotNull(
                    log.type.name,
                    TimeUtils.formatMillis(log.millis, "HH:mm:ss"),
                    log.tag,
                ).joinToString(" · ")
                textLogMessage.text = log.message
                root.setOnLongClickListener {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("log", log.message))
                    Toast.makeText(context, translate {
                        Locale.ENGLISH { "Copied the log to the clipboard." }
                        Locale.KOREAN  { "로그를 클립보드에 복사했어요." }
                    }, Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }

        private fun colorOf(type: LogType): Int =
            when (type) {
                LogType.INFO     -> R.color.code_string
                LogType.DEBUG    -> R.color.code_purple
                LogType.WARN     -> R.color.code_yellow
                LogType.ERROR    -> R.color.code_orange
                LogType.CRITICAL -> R.color.code_error
                LogType.VERBOSE  -> R.color.monokai_pro_sky
            }

        class ViewHolder(val binding: ItemEditorLogBinding): RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        private const val ARG_PROJECT_ID = "projectId"

        @JvmStatic
        fun newInstance(projectId: String) =
            EditorLogsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROJECT_ID, projectId)
                }
            }
    }
}
