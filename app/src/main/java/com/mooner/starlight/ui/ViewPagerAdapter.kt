package com.mooner.starlight.ui

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.mooner.starlight.MainActivity
import com.mooner.starlight.ui.home.HomeFragment
import com.mooner.starlight.ui.plugins.PluginsFragment
import com.mooner.starlight.ui.projects.ProjectsFragment
import com.mooner.starlight.ui.settings.SettingsFragment

class ViewPagerAdapter(activity: MainActivity): FragmentStateAdapter(activity) {

    companion object {
        const val PAGE_COUNT = 4
    }

    override fun getItemCount(): Int = PAGE_COUNT

    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> HomeFragment()
            1 -> ProjectsFragment()
            2 -> PluginsFragment()
            3 -> SettingsFragment()
            else -> HomeFragment()
        }
    }

}