/*
 * FileTreeFragment.kt created by Minki Moon(mooner1022) on 23. 1. 12. 오후 8:23
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.editor.drawer

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BasicGridItem
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.gridItems
import com.afollestad.materialdialogs.customview.customView
import dev.mooner.peekalert.PeekAlert
import dev.mooner.peekalert.createPeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentFileTreeBinding
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import dev.mooner.starlight.utils.dp
import dev.mooner.starlight.utils.getTypeface
import dev.mooner.starlight.utils.setCommonAttrs
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
                (activity as DefaultEditorActivity).apply {
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
            showNewFileDialog {
                treeAdapter.requestUpdate()
            }
        }

        return binding.root
    }

    @SuppressLint("CheckResult")
    private fun showNewFileDialog(onCreate: () -> Unit) {
        var type: Int = -1

        MaterialDialog(requireActivity(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            val titleDir = context.getString(R.string.directory)
            val titleFile = context.getString(R.string.file)

            setCommonAttrs()
            val items = listOf(
                BasicGridItem(
                    iconRes = R.drawable.ic_round_folder_24,
                    title = titleDir
                ),
                BasicGridItem(
                    iconRes = R.drawable.ic_round_file_24,
                    title = titleFile
                )
            )
            gridItems(items, waitForPositiveButton = true) { _, _, item ->
                type = when(item.title) {
                    titleFile   -> TYPE_FILE
                    titleDir    -> TYPE_DIR
                    else        -> TYPE_FILE
                }
            }
            customView(R.layout.dialog_new_file)
            noAutoDismiss()

            positiveButton(res = R.string.ok) {
                val nameInput = findViewById<EditText>(R.id.edit_file_name)!!
                if (nameInput.text.isEmpty()) {
                    nameInput.error = "파일/경로 이름을 입력해주세요."
                    nameInput.requestFocus()
                    return@positiveButton
                }
                if (!"(^[-_.A-Za-z0-9/]+\$)".toRegex().matches(nameInput.text.toString())) {
                    nameInput.error = "허용되지 않는 문자가 포함되어 있어요."
                    nameInput.requestFocus()
                    return@positiveButton
                }

                val file = File(parent, nameInput.text.toString())
                if (file.exists()) {
                    nameInput.error = "이미 존재하는 이름이에요."
                    nameInput.requestFocus()
                    return@positiveButton
                }

                when (type) {
                    TYPE_FILE -> {
                        file.parentFile?.let { parent ->
                            if (!parent.exists())
                                parent.mkdirs()
                        }
                        file.createNewFile()
                    }
                    TYPE_DIR ->
                        file.mkdirs()
                    -1 -> {
                        createPeekAlert(this@FileTreeFragment) {
                            iconTint(R.color.white)
                            iconRes = R.drawable.ic_round_error_outline_24
                            position = PeekAlert.Position.Top
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            cornerRadius = dp(14).toFloat()
                            autoHideMillis = 2000L
                            text("생성할 종류를 선택해 주세요. (폴더/파일)") {
                                textColor(R.color.white)
                                textSize = 14f
                                typeface = getTypeface(requireContext(), R.font.nanumsquare_round_bold)
                            }
                            backgroundColor(R.color.orange)
                        }.peek()
                        return@positiveButton
                    }
                }
                onCreate()
                it.dismiss()
            }
        }
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