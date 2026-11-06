package dev.mooner.starlight.ui.tree

open class Node<T>(val content: T) {

    var parent: Node<T>? = null

    private val _children = mutableListOf<Node<T>>()
    val children: List<Node<T>>
        get() = _children

    var expanded = false
        private set

    val isRoot: Boolean
        get() = parent == null

    val isLeaf: Boolean
        get() = children.isEmpty()

    private var _depth = UNDEFINED
    val depth: Int
        get() {
            if (isRoot)
                _depth = 0
            else if (_depth == UNDEFINED)
                _depth = parent!!.depth + 1
            return _depth
        }

    fun toggle() {
        expanded = !expanded
    }

    fun expand() {
        if (!expanded)
            expanded = true
    }

    fun expandAll() {
        expand()

        if (isLeaf) return
        children.forEach(Node<T>::expandAll)
    }

    fun collapse() {
        if (expanded)
            expanded = false
    }

    fun collapseAll() {
        collapse()

        if (isLeaf) return
        children.forEach { it.collapseAll() }
    }

    fun addChild(vararg children: Node<T>): Node<T> {
        _children += children
        for (child in children)
            child.parent = this

        return this
    }

    companion object {
        private const val UNDEFINED = -1
    }
}