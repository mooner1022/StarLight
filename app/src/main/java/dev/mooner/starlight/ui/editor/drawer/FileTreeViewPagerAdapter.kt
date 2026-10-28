/*
 * FileTreeViewPagerAdapter.kt created by Minki Moon(mooner1022) on 23. 1. 12. 오후 8:20
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.editor.drawer

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import dev.mooner.starlight.R

class FileTreeViewPagerAdapter(
    fragment: FileTreeDrawerFragment,
    private val mainScript: String?,
    private val projectId: String?,
    private val rootPath: String
): FragmentStateAdapter(fragment) {

    val logsPosition: Int?
        get() = if (projectId != null) 1 else null

    val problemsPosition: Int
        get() = itemCount - 1

    override fun getItemCount(): Int =
        if (projectId != null) 3 else 2

    @StringRes
    fun titleAt(position: Int): Int =
        when (position) {
            0                -> R.string.menu_file_tree
            problemsPosition -> R.string.editor_drawer_problems
            else             -> R.string.title_logs
        }

    override fun createFragment(position: Int): Fragment =
        when (position) {
            0                -> FileTreeFragment.newInstance(rootPath, mainScript)
            problemsPosition -> EditorMessageFragment()
            else             -> EditorLogsFragment.newInstance(projectId!!)
        }
}
