/*
 * FileTreeViewPagerAdapter.kt created by Minki Moon(mooner1022) on 23. 1. 12. 오후 8:20
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.editor.drawer

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class FileTreeViewPagerAdapter(
    fragment: FileTreeDrawerFragment,
    private val fileTreeAdapter: FileTreeAdapter
): FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int =
        PAGE_COUNT

    override fun createFragment(position: Int): Fragment =
        when(position) {
            0 -> FileTreeFragment(fileTreeAdapter)
            1 -> EditorMessageFragment()
            else -> createFragment(0)
        }

    companion object {
        private const val PAGE_COUNT = 2
    }
}