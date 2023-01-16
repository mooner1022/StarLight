package dev.mooner.starlight.ui.editor.drawer

import android.content.Context
import coil.transform.RoundedCornersTransformation
import dev.mooner.starlight.R
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import dev.mooner.starlight.ui.tree.Node
import dev.mooner.starlight.ui.tree.TreeAdapter
import dev.mooner.starlight.utils.dp
import dev.mooner.starlight.utils.getLanguageByExtension
import dev.mooner.starlight.utils.loadWithTint
import java.io.File

typealias OnFileSelectedListener = (file: File) -> Unit
typealias FileNode = Node<File>

class FileTreeAdapter(
    context: Context,
    val root: File,
    val listener: OnFileSelectedListener
): TreeAdapter<File>(context, walkAndMap(root)) {

    override fun onBindViewHolder(holder: TreeViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val node = displayedNodes[position]
        val file = node.content

        val icon = when(getLanguageByExtension(file.extension)) {
            DefaultEditorActivity.Language.JAVASCRIPT -> R.drawable.ic_js
            DefaultEditorActivity.Language.PYTHON -> R.drawable.ic_python
            else -> {
                if (file.isDirectory)
                    R.drawable.ic_round_folder_24
                else
                    null
            }
        }

        icon?.let { holder.icon.loadWithTint(it, null) {
            transformations(RoundedCornersTransformation(dp(4).toFloat()))
        } }
            ?: holder.icon.loadWithTint(R.drawable.ic_round_code_24, R.color.main_bright)

        holder.name.text = file.name

        if (node.isLeaf)
            holder.root.setOnClickListener {
                listener(node.content)
            }
    }

    companion object {
        private fun walkAndMap(root: File): List<FileNode> {
            val result: MutableList<FileNode> = arrayListOf()

            root.walk().maxDepth(1).drop(1).forEach {
                val node = FileNode(it)
                println(it.path)
                if (it.isDirectory)
                    node.addChild(*walkAndMap(it).toTypedArray())
                result += node
            }
            return result
        }
    }
}