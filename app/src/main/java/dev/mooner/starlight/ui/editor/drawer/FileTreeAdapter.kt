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

    private val editor: DefaultEditorActivity?
        get() = activity as? DefaultEditorActivity

    var currentFile: File? = null

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

        val isCurrent = file == currentFile
        holder.name.text = file.name
        holder.name.setTextColor(activity.getColor(if (isCurrent) R.color.main_dark else R.color.text))
        holder.name.typeface = getTypeface(activity, if (isCurrent) R.font.nanumsquare_neo_bold else R.font.nanumsquare_neo_regular)

        if (file.isFile)
            holder.root.setOnClickListener {
                listener(node.content)
            }

        holder.root.setOnLongClickListener {
            showFileMenu(holder, file)
            true
        }
    }

    private fun showFileMenu(holder: TreeViewHolder, file: File) {
        val isLocked = file.relativeTo(root).path in lockedFiles
        val newFile = translate {
            Locale.ENGLISH { "New file" }
            Locale.KOREAN  { "파일 생성" }
        }
        val rename = translate {
            Locale.ENGLISH { "Rename" }
            Locale.KOREAN  { "이름 바꾸기" }
        }
        val delete = translate {
            Locale.ENGLISH { "Delete" }
            Locale.KOREAN  { "삭제" }
        }
        holder.name.showPopup {
            if (file.isDirectory)
                item(newFile) {
                    this.icon = Icon.ADD.drawableRes
                    iconColor(color { "#C4D9FF" })
                }
            item(rename) {
                this.icon = Icon.EDIT.drawableRes
                this.enabled = !isLocked
                iconColor(color { "#A6CDC6" })
            }
            item(delete) {
                this.icon = R.drawable.twotone_delete_24
                this.enabled = !isLocked
                iconColor(color { "#FF6188" })
            }
        }.onEach { title ->
            when (title) {
                newFile ->
                    activity.showNewFileDialog(file, ::onFileCreated)
                rename ->
                    showRenameFileDialog(file)
                delete -> {
                    if (isLocked)
                        Toast.makeText(holder.root.context, translate {
                            Locale.ENGLISH { "This file cannot be deleted." }
                            Locale.KOREAN  { "삭제할 수 없는 파일이에요." }
                        }, Toast.LENGTH_SHORT).show()
                    else
                        showDeleteConfirmDialog(holder.root.context, file)
                }
            }
        }.launchIn(CoroutineScope(Dispatchers.Main))
    }

    fun onFileCreated(file: File) {
        requestUpdate()
        if (file.isFile)
            listener(file)
    }

    fun requestUpdate(moved: Pair<File, File>? = null) {
        val expanded = displayedNodes
            .filter { it.expanded }
            .mapTo(hashSetOf()) { node ->
                val (from, to) = moved ?: return@mapTo node.content
                if (node.content.startsWith(from)) to.resolve(node.content.relativeTo(from)) else node.content
            }
        super.recreateWith(walkAndMap(root)) { it.content in expanded }
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
                if (!confirm)
                    return@showConfirmDialog
                file.deleteRecursively()
                editor?.onFileDeleted(file)
                requestUpdate()
            }
        )
    }

    @SuppressLint("CheckResult")
    private fun showRenameFileDialog(target: File) {
        MaterialDialog(activity, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            setCommonAttrs()
            customView(R.layout.dialog_editor_rename_file)
            noAutoDismiss()

            val nameInput: EditText = findViewById(R.id.edit_text_filename)!!
            nameInput.hint = target.name
            nameInput.setText(target.name)

            positiveButton(res = R.string.ok) {
                if (nameInput.text.toString() == target.name)
                    return@positiveButton it.dismiss()
                val error = validateFileName(nameInput.text.toString(), target.parentFile)
                if (error != null) {
                    nameInput.error = error
                    nameInput.requestFocus()
                    return@positiveButton
                }

                val file = File(target.parentFile, nameInput.text.toString())
                val onRenamed = { renamed: Boolean ->
                    if (renamed) {
                        requestUpdate(target to file)
                        it.dismiss()
                    } else
                        nameInput.error = translate {
                            Locale.ENGLISH { "Failed to rename the file." }
                            Locale.KOREAN  { "이름을 바꾸지 못했어요." }
                        }
                }
                editor?.renameFile(target, file, onRenamed)
                    ?: onRenamed(target.renameTo(file))
            }
        }
    }

    companion object {
        private fun walkAndMap(root: File): List<FileNode> {
            val result: MutableList<FileNode> = arrayListOf()

            root.walk().maxDepth(1).drop(1).forEach {
                val node = FileNode(it)
                if (it.isDirectory)
                    node.addChild(*walkAndMap(it).toTypedArray())
                result += node
            }
            result.sortWith(compareBy<FileNode> { !it.content.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.content.name })
            return result
        }
    }
}
