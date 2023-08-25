/*
 * ProjectEventManager.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project.event

object ProjectEventManager {

    private const val WILDCARD_ALL   = "*"
    private const val PATH_DELIMITER = '.'
    private val       ID_SANITY_REGEX = "(^[-_\\dA-Za-z]+\$)".toRegex()

    private val classCache: MutableMap<ProjectEventClass, String> = hashMapOf()
    private val events: MutableMap<String, ProjectEventNode> = hashMapOf()
    private val rootNode = ProjectEventNode("", "", events)

    @Suppress("unused")
    fun dump(): String {
        return buildString {
            for ((k, v) in events) {
                append(k)
                append(".")
                append(recDump(v))
            }
        }
    }

    private fun recDump(node: ProjectEventNode): String {
        return buildString {
            for ((k, v) in node.tree) {
                append(k)
                append("(").append(v.child).append(")")
                append(".")
                append(recDump(v))
            }
            append("/")
        }
    }

    fun validateAndGetEventID(eventClass: ProjectEventClass): String? =
        classCache[eventClass]

    fun register(id: String, event: ProjectEventClass): Boolean {
        if (isIdUsed(id))
            return false
        val path = id.split(PATH_DELIMITER)
        if (path.isEmpty() || (path.size == 1 && path[0] != "starlight"))
            throw IllegalArgumentException("Invalid ID: should have at least one separator(.)")

        recursiveRegister(path, 0, rootNode, event)
        classCache[event] = id
        return true
    }

    private fun recursiveRegister(path: List<String>, cursor: Int, node: ProjectEventNode, event: ProjectEventClass) {
        val curPath = path[cursor]
        if (curPath.isBlank() || !ID_SANITY_REGEX.matches(curPath))
            throw IllegalArgumentException("Invalid ID: invalid character: $curPath")
        val isEndpoint = path.size - 1 == cursor

        val mNode = (node.tree[curPath] ?: ProjectEventNode(
            curPath,
            path.subList(0, cursor + 1)
                .joinToString(PATH_DELIMITER.toString())
        ).also {
            node.tree[curPath] = it
        })
        if (isEndpoint)
            mNode.child = event
        else
            recursiveRegister(path, cursor + 1, mNode, event)
    }

    fun isIdUsed(id: String): Boolean {
        return findFirst(id) != null
    }

    fun filterAllowedEvents(ids: List<String>): Set<String> =
        filterAllowedEvents(ids.toSet())

    fun filterAllowedEvents(ids: Set<String>): Set<String> {
        val result: MutableSet<String> = hashSetOf()
        for (id in ids)
            findAllID(id).forEach(result::add)
        return result
    }

    private fun findAllID(wildcard: String): Set<String> =
        recursiveIDFilter(wildcard.split("."), 0, rootNode)

    private fun recursiveIDFilter(wildcard: List<String>, idx: Int, node: ProjectEventNode): Set<String> {
        return when(val cursor = wildcard[idx]) {
            WILDCARD_ALL ->
                getAllIDWithChildren(node)
            else -> {
                if (cursor !in node.tree)
                    return emptySet()

                if (idx == wildcard.size - 1) {
                    val mNode = node.tree[cursor]!!
                    if (mNode.child == null)
                        return emptySet()

                    return hashSetOf(mNode.fullPath)
                }

                recursiveIDFilter(wildcard, idx + 1, node.tree[cursor]!!)
            }
        }
    }

    fun findFirst(id: String): ProjectEventClass? =
        find(id).let { if (it.isEmpty()) null else it.first() }

    fun find(id: String): Set<ProjectEventClass> {
        if (id.isBlank())
            return emptySet()

        val path = id.split(PATH_DELIMITER)
        return recursiveFind(path, 0, rootNode)
    }

    private fun recursiveFind(path: List<String>, cursor: Int, node: ProjectEventNode): Set<ProjectEventClass> {
        return when(val curPath = path[cursor]) {
            WILDCARD_ALL ->
                getAllChildren(node)
            else -> {
                if (curPath !in node.tree)
                    return emptySet()

                if (cursor == path.size - 1) {
                    val mNode = node.tree[path[cursor]]!!
                    if (mNode.child == null)
                        return emptySet()

                    return hashSetOf(mNode.child!!)
                }

                recursiveFind(path, cursor + 1, node.tree[curPath]!!)
            }
        }
    }

    private fun getAllIDWithChildren(node: ProjectEventNode): Set<String> =
        getAllNodeWithChild(node)
            .map(ProjectEventNode::fullPath)
            .toSet()

    private fun getAllChildren(node: ProjectEventNode): Set<ProjectEventClass> =
        getAllNodeWithChild(node)
            .mapNotNull(ProjectEventNode::child)
            .toSet()

    private fun getAllNodeWithChild(node: ProjectEventNode): Set<ProjectEventNode> {
        val result: MutableSet<ProjectEventNode> = hashSetOf()
        if (node.child != null)
            result += node

        for ((_, mNode) in node.tree) {
            getAllNodeWithChild(mNode).forEach(result::add)
        }
        return result
    }

    private data class ProjectEventNode(
        val path     : String,
        val fullPath : String,
        val tree     : MutableMap<String, ProjectEventNode> = hashMapOf(),
        var child    : ProjectEventClass? = null
    )
}