/*
 * EditorLogsFragment.kt created by Minki Moon(mooner1022) on 12/16/23, 1:42 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.editor.drawer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import dev.mooner.starlight.databinding.FragmentEditorLogsBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.ui.logs.LogItem
import dev.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import java.util.*
import kotlin.properties.Delegates.notNull

class EditorLogsFragment: Fragment() {

    private var _binding: FragmentEditorLogsBinding? = null
    val binding get() = _binding!!

    private var project: Project by notNull()
    private var listenerId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectId = arguments?.getString(ARG_PROJECT_ID)
            ?.let(UUID::fromString)
            ?: throw IllegalArgumentException("Failed to retrieve project id")

        project = Session.projectManager.getProjects().find { it.info.id == projectId }
            ?: throw IllegalArgumentException("Unable to find project with id: $projectId")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorLogsBinding.inflate(inflater, container, false)

        val logger = project.logger
        val mAdapter = LogsRecyclerViewAdapter()
            .withData(logger.logs, LogItem.ViewType.TEXT)
        println(logger.logs.size)

        val mLayoutManager = LinearLayoutManager(activity).apply {
            reverseLayout = true
            stackFromEnd = true
        }
        binding.rvEditorLogs.apply {
            layoutManager = mLayoutManager
            adapter = mAdapter
        }
        mAdapter.notifyItemRangeInserted(0, logger.logs.size)

        listenerId = logger.addOnLogCreateListener { log ->
            binding.rvEditorLogs.post {
                mAdapter.push(log)
                binding.rvEditorLogs.smoothScrollToPosition(mAdapter.getItems().size - 1)
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerId?.let(project.logger::removeOnLogCreateListener)
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