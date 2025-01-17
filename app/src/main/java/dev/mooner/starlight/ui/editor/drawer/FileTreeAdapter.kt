package dev.mooner.starlight.ui.editor.drawer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import coil.transform.RoundedCornersTransformation
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.customview.customView
import dev.mooner.configdsl.Icon
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.color
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import dev.mooner.starlight.ui.tree.Node
import dev.mooner.starlight.ui.tree.TreeAdapter
import dev.mooner.starlight.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File

typealias OnFileSelectedListener = (file: File) -> Unit
typealias FileNode = Node<File>

class FileTreeAdapter(
    private val activity: Activity,
    val root: File,
    private val lockedFiles: Set<String>,
    val listener: OnFileSelectedListener
): TreeAdapter<File>(activity, walkAndMap(root)) {

    override fun onBindViewHolder(holder: TreeViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val node = displayedNodes[position]
        val file = node.content

        val icon = when(getLanguageByExtension(file.extension)) {
            DefaultEditorActivity.Language.JAVASCRIPT -> R.drawable.ic_js
            DefaultEditorActivity.Language.PYTHON     -> R.drawable.ic_python
            else -> {
                if (file.isDirectory)
                    dev.mooner.configdsl.R.drawable.ic_round_folder_24
                else
                    null
            }
        }

        icon?.let { holder.icon.loadWithTint(it, null) {
            transformations(RoundedCornersTransformation(dp(4).toFloat()))
        } }
            ?: holder.icon.loadWithTint(R.drawable.ic_round_code_24, R.color.main_bright)

        holder.name.text = file.name

        if (node.isLeaf && node.content.isFile)
            holder.root.setOnClickListener {
                listener(node.content)
            }

        holder.root.setOnLongClickListener {
            /*
            if (file.name in lockedFiles)
                Toast.makeText(holder.root.context, "삭제할 수 없는 파일이에요.", Toast.LENGTH_SHORT).show()
            else
                showDeleteConfirmDialog(it.context, file)
             */
            holder.name.showPopup {
                if (node.content.isDirectory)
                    item("파일 생성") {
                        this.icon = Icon.ADD.drawableRes
                        iconColor(color { "#C4D9FF" })
                    }
                item("이름 바꾸기") {
                    this.icon = Icon.EDIT.drawableRes
                    this.enabled = file.name !in lockedFiles
                    iconColor(color { "#A6CDC6" })
                }
                item("삭제") {
                    this.icon = R.drawable.twotone_delete_24
                    this.enabled = file.name !in lockedFiles
                    iconColor(color { "#FF6188" })
                }
            }.onEach { id ->
                when (id) {
                    "파일 생성" ->
                        activity.showNewFileDialog(file) { this.requestUpdate() }
                    "이름 바꾸기" ->
                        showRenameFileDialog(file) { this.requestUpdate() }
                    "삭제" -> {
                        if (file.name in lockedFiles)
                            Toast.makeText(holder.root.context, "삭제할 수 없는 파일이에요.", Toast.LENGTH_SHORT).show()
                        else
                            showDeleteConfirmDialog(it.context, file)
                    }
                }
            }.launchIn(CoroutineScope(Dispatchers.Main))

            true
        }
    }

    fun requestUpdate() {
        super.recreateWith(walkAndMap(root))
    }

    private fun showDeleteConfirmDialog(context: Context, file: File) {
        showConfirmDialog(
            context = context,
            title = translate {
                Locale.ENGLISH { "⚠️ Delete file [${file.name}]?" }
                Locale.KOREAN  { "⚠️ 파일 [${file.name}]을(를) 삭제할까요?" }
            },
            message = translate {
                Locale.ENGLISH { "After deletion, it cannot be reversed.\nAre you sure you want to delete this file?" }
                Locale.KOREAN  { "삭제 후에는 되돌릴 수 없어요.\n정말 이 파일을 삭제할까요?" }
            },
            onDismiss = { confirm ->
                if (confirm)
                    file.deleteRecursively()
                requestUpdate()
            }
        )
    }

    @SuppressLint("CheckResult")
    private fun showRenameFileDialog(target: File, onNameUpdated: (file: File) -> Unit) {
        MaterialDialog(activity, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            setCommonAttrs()
            customView(R.layout.dialog_editor_rename_file)
            noAutoDismiss()

            val nameInput: EditText = findViewById(R.id.edit_text_filename)!!
            nameInput.hint = target.name
            nameInput.setText(target.name)

            positiveButton(res = R.string.ok) {
                if (nameInput.text.isEmpty()) {
                    nameInput.error = "폴더/파일의 이름을 입력해주세요."
                    nameInput.requestFocus()
                    return@positiveButton
                }
                if (!"(^[-_.A-Za-z0-9/]+\$)".toRegex().matches(nameInput.text.toString())) {
                    nameInput.error = "허용되지 않는 문자가 포함되어 있어요."
                    nameInput.requestFocus()
                    return@positiveButton
                }

                val nRoot = target.parentFile
                val file = File(nRoot, nameInput.text.toString())
                if (file.exists()) {
                    nameInput.error = "이미 존재하는 이름이에요."
                    nameInput.requestFocus()
                    return@positiveButton
                }

                target.renameTo(file)

                onNameUpdated(file)
                it.dismiss()
            }
        }
    }

    companion object {
        private fun walkAndMap(root: File): List<FileNode> {
            val result: MutableList<FileNode> = arrayListOf()

            root.walk().maxDepth(1).drop(1).forEach {
                val node = FileNode(it)
                //println(it.path)
                if (it.isDirectory)
                    node.addChild(*walkAndMap(it).toTypedArray())
                result += node
            }
            return result
        }
    }
}