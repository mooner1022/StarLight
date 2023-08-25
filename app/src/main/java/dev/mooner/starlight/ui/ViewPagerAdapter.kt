package dev.mooner.starlight.ui

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import dev.mooner.starlight.MainActivity
import dev.mooner.starlight.ui.home.HomeFragment
import dev.mooner.starlight.ui.logs.LogsFragment
import dev.mooner.starlight.ui.plugins.PluginsFragment
import dev.mooner.starlight.ui.projects.ProjectsFragment
import dev.mooner.starlight.ui.settings.SettingsFragment

class ViewPagerAdapter(activity: MainActivity): FragmentStateAdapter(activity) {

    companion object {
        const val PAGE_COUNT = 5
    }

    override fun getItemCount(): Int = PAGE_COUNT

    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> HomeFragment()
            1 -> ProjectsFragment()
            2 -> PluginsFragment()
            3 -> LogsFragment()
            4 -> SettingsFragment()
            else -> HomeFragment()
        }
    }

}