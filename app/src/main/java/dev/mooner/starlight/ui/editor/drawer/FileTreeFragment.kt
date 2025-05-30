/*
 * FileTreeFragment.kt created by Minki Moon(mooner1022) on 23. 1. 12. 오후 8:23
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.editor.drawer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import dev.mooner.starlight.databinding.FragmentFileTreeBinding
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import dev.mooner.starlight.utils.showNewFileDialog
import dev.mooner.starlight.utils.toFile
import java.io.File
import kotlin.properties.Delegates.notNull

class FileTreeFragment : Fragment() {

    private var _binding: FragmentFileTreeBinding? = null
    private val binding get() = _binding!!

    private var parent: File by notNull()
    private var mainScript: String? = null
    private var treeAdapter: FileTreeAdapter by notNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        parent = arguments?.getString(ARG_FILE_PATH)?.toFile() ?: getStarLightDirectory()
        mainScript = arguments?.getString(ARG_MAIN_SCRIPT)

        val activity = requireActivity()
        val isEditor = activity is DefaultEditorActivity

        val lockedFiles = mainScript
            ?.let { LOCKED_FILE_NAMES + it }
            ?: LOCKED_FILE_NAMES
        treeAdapter = if (isEditor)
            FileTreeAdapter(activity, parent, lockedFiles) { file ->
                activity.apply {
                    openFile(file)
                    closeDrawer(GravityCompat.START, true)
                }
            }
        else
            FileTreeAdapter(activity, parent, lockedFiles) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileTreeBinding.inflate(inflater, container, false)

        binding.rvFileTree.apply {
            adapter = treeAdapter
            itemAnimator = null
        }

        treeAdapter.notifyItemRangeChanged(0, treeAdapter.itemCount)

        binding.buttonNewFile.setOnClickListener {
            requireActivity().showNewFileDialog(parent) {
                treeAdapter.requestUpdate()
            }
        }

        return binding.root
    }

    companion object {

        private const val ARG_MAIN_SCRIPT = "mainScript"
        private const val ARG_FILE_PATH   = "filePath"
        private const val TYPE_FILE       = 0
        private const val TYPE_DIR        = 1

        private val LOCKED_FILE_NAMES = setOf(
            "project.json",
            "config.json",
        )

        fun newInstance(rootPath: String, mainScript: String? = null) =
            FileTreeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILE_PATH, rootPath)
                    mainScript?.let { putString(ARG_MAIN_SCRIPT, it) }
                }
            }
    }
}