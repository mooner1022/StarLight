package dev.mooner.starlight.ui.tree

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.starlight.databinding.TreeNodeBinding

abstract class TreeAdapter <T> (
    private val context: Context,
    private var fullNode: List<Node<T>>
): RecyclerView.Adapter<TreeAdapter<T>.TreeViewHolder>() {

    protected val displayedNodes: MutableList<Node<T>> = arrayListOf()

    private fun displayNodes(nodes: List<Node<T>>) {
        for (node in nodes) {
            displayedNodes += node
            if (node.isLeaf)
                node.collapse()
        }
    }

    open fun toggleExpand(node: Node<T>) {
        if (node.isLeaf) return

        (displayedNodes.indexOf(node) + 1).let { index ->
            if (node.expanded)
                notifyItemRangeRemoved(index, removeChildNodes(node, true))
            else
                notifyItemRangeInserted(index, addChildNodes(node, index))
        }
    }

    fun replaceAll(nodes: List<Node<T>>) {
        displayedNodes.clear()
        displayNodes(nodes)
        notifyItemRangeChanged(0, displayedNodes.size)
    }

    private fun addChildNodes(parent: Node<T>, startIndex: Int): Int {
        var added = 0

        for (child in parent.children) {
            displayedNodes.add(startIndex + added++, child)
            if (child.expanded) {
                added += addChildNodes(child, startIndex + added)
            }
        }

        parent.expand()

        return added
    }

    private fun removeChildNodes(parent: Node<T>, collapse: Boolean = true): Int {
        if (parent.isLeaf) return 0

        var removed = parent.children.size

        displayedNodes.removeAll(parent.children)

        for (child in parent.children) {
            if (child.expanded) {
                child.toggle()
                removed += removeChildNodes(child, true)
            }
        }

        if (collapse) parent.collapse()
        return removed
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreeViewHolder {
        val binding = TreeNodeBinding.inflate(LayoutInflater.from(context), parent, false)
        return TreeViewHolder(binding)
    }

    @CallSuper
    override fun onBindViewHolder(holder: TreeViewHolder, position: Int) {
        val node = displayedNodes[position]

        // Show toggle arrow if parent
        if (node.isLeaf) {
            holder.arrow.visibility = View.INVISIBLE
            //holder.arrow.setOnClickListener(null)
            holder.root.setOnClickListener(null)
        } else {
            //holder.arrow.setOnClickListener { toggleExpand(node) }
            holder.arrow.apply {
                visibility = View.VISIBLE
                rotation = if (node.expanded) 0f else -90f
            }
            holder.root.setOnClickListener {
                toggleExpand(node)
                holder.arrow.rotation = if (node.expanded) 0f else -90f
            }
        }
        holder.setIndent(node)
    }

    override fun getItemCount(): Int =
        displayedNodes.size

    @SuppressLint("NotifyDataSetChanged")
    protected fun recreateWith(fullNode: List<Node<T>>) {
        fullNode.forEach { println("R: " + it.content) }
        this.fullNode = fullNode
        replaceAll(fullNode)
    }

    init {
        displayNodes(fullNode)
    }

    companion object {
        private const val DEF_PADDING = 50
    }

    inner class TreeViewHolder(
        private val binding: TreeNodeBinding
    ): RecyclerView.ViewHolder(binding.root) {
        val root: ConstraintLayout = binding.root
        val arrow: ImageView = binding.arrowCollapse
        val icon: ImageView  = binding.imageViewIcon
        val name: TextView   = binding.textViewLabel

        fun <T> setIndent(node: Node<T>) {
            binding.root.apply {
                setPadding(node.depth * DEF_PADDING, paddingTop, paddingRight, paddingBottom)
            }
        }
    }
}