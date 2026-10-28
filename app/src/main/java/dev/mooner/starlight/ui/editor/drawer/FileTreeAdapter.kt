package dev.mooner.starlight.ui.editor.drawer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.text.format.Formatter
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import coil.transform.RoundedCornersTransformation
import dev.mooner.configdsl.Icon
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.color
import dev.mooner.starlight.ui.common.showSheet
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

class FileNode(file: File, val size: Long): Node<File>(file)

class FileTreeAdapter(
    private val activity: Activity,
    val root: File,
    private val lockedFiles: Set<String>,
    val listener: OnFileSelectedListener
): TreeAdapter<File>(activity, walkAndMap(root)) {

    private val editor: DefaultEditorActivity?
        get() = activity as? DefaultEditorActivity

    var currentFile: File? = null

    var onTreeChanged: (() -> Unit)? = null

    val fileCount: Int
        get() = countNodes(rootNodes) { it.content.isFile }

    val directoryCount: Int
        get() = countNodes(rootNodes) { it.content.isDirectory }

    override fun onBindViewHolder(holder: TreeViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val node = displayedNodes[position] as FileNode
        val file = node.content
        val context = holder.root.context

        val language = getLanguageByExtension(file.extension)
        when {
            file.isDirectory ->
                holder.icon.setTintedIcon(dev.mooner.configdsl.R.drawable.ic_round_folder_24, R.color.main_bright)
            language == DefaultEditorActivity.Language.JAVASCRIPT || language == DefaultEditorActivity.Language.PYTHON ->
                holder.icon.loadWithTint(language.icon!!, null) {
                    transformations(RoundedCornersTransformation(dp(4).toFloat()))
                }
            language == null || language in PLAIN_LANGUAGES ->
                holder.icon.setTintedIcon(dev.mooner.configdsl.R.drawable.ic_round_file_24, R.color.text_sub)
            else ->
                holder.icon.setTintedIcon(R.drawable.ic_round_code_24, R.color.main_bright)
        }

        val isCurrent = file == currentFile
        holder.root.setBackgroundResource(if (isCurrent) R.drawable.bg_tree_node_current else 0)
        holder.name.text = file.name
        holder.name.setTextColor(context.getColor(if (isCurrent) R.color.main_dark else R.color.text))
        holder.name.typeface = getTypeface(context, if (isCurrent) R.font.nanumsquare_neo_bold else R.font.nanumsquare_neo_regular)
        holder.meta.text = if (file.isDirectory)
            context.resources.getQuantityString(R.plurals.editor_folder_items, node.children.size, node.children.size)
        else
            Formatter.formatShortFileSize(context, node.size)

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
                    activity.showNewFileDialog(file, onFileCreated = ::onFileCreated)
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
        onTreeChanged?.invoke()
    }

    private fun android.widget.ImageView.setTintedIcon(res: Int, tint: Int) {
        loadWithTint(res, tint)
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
        val content = LayoutInflater.from(activity).inflate(R.layout.dialog_editor_rename_file, null)
        val nameInput: EditText = content.findViewById(R.id.edit_text_filename)
        nameInput.hint = target.name
        nameInput.setText(target.name)

        activity.showSheet {
            title = translate {
                Locale.ENGLISH { "Rename" }
                Locale.KOREAN  { "이름 변경" }
            }
            icon = R.drawable.ic_round_edit_24
            content(content)
            negative(R.string.cancel)
            positive(R.string.ok, autoDismiss = false) {
                if (nameInput.text.toString() == target.name)
                    return@positive it.dismiss()
                val error = validateFileName(nameInput.text.toString(), target.parentFile)
                if (error != null) {
                    nameInput.error = error
                    nameInput.requestFocus()
                    return@positive
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
        private val PLAIN_LANGUAGES = setOf(
            DefaultEditorActivity.Language.PLAIN_TEXT,
            DefaultEditorActivity.Language.MARKDOWN,
            DefaultEditorActivity.Language.JSON,
        )

        private fun countNodes(nodes: List<Node<File>>, predicate: (Node<File>) -> Boolean): Int =
            nodes.sumOf { (if (predicate(it)) 1 else 0) + countNodes(it.children, predicate) }

        private fun walkAndMap(root: File): List<FileNode> {
            val result: MutableList<FileNode> = arrayListOf()

            root.walk().maxDepth(1).drop(1).forEach {
                val node = FileNode(it, if (it.isFile) it.length() else 0L)
                if (it.isDirectory)
                    node.addChild(*walkAndMap(it).toTypedArray())
                result += node
            }
            result.sortWith(compareBy<FileNode> { !it.content.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.content.name })
            return result
        }
    }
}
