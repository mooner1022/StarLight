/*
 * MainActivity.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import dev.mooner.starlight.databinding.ActivityMainBinding
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.ui.ViewPagerAdapter
import dev.mooner.starlight.utils.LAYOUT_TABLET
import dev.mooner.starlight.utils.layoutMode
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            val id = when(position) {
                0 -> R.id.nav_home
                1 -> R.id.nav_projects
                2 -> R.id.nav_plugins
                3 -> R.id.nav_settings
                else -> R.id.nav_home
            }
            binding.bottomMenu.setItemSelected(id, true)
            onPageChanged(id)
        }
    }
    private val onMenuItemSelectedListener = { id: Int ->
        val index = when(id) {
            R.id.nav_home -> 0
            R.id.nav_projects -> 1
            R.id.nav_plugins -> 2
            R.id.nav_settings -> 3
            else -> 0
        }
        binding.viewPager.setCurrentItem(index, true)
        onPageChanged(id)
    }
    private val onOffsetChangedListener = AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
        val percent = 1.0f - abs(
            verticalOffset / appBarLayout.totalScrollRange
                .toFloat()
        )
        binding.statusText.alpha = percent
        binding.titleText.alpha = percent
        binding.imageViewLogo.alpha = percent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.apply {
            viewPager.adapter = ViewPagerAdapter(this@MainActivity)
            viewPager.registerOnPageChangeCallback(onPageChangeCallback)

            bottomMenu.setItemSelected(R.id.nav_home, isSelected = true)
            bottomMenu.setOnItemSelectedListener(onMenuItemSelectedListener)
            if (layoutMode == LAYOUT_TABLET) {
                buttonExpandMenu?.setOnClickListener {
                    bottomMenu.let { menu ->
                        if (menu.isExpanded()) {
                            TransitionManager.beginDelayedTransition(root as ViewGroup, ChangeBounds())
                            menu.collapse()
                        } else {
                            TransitionManager.beginDelayedTransition(root as ViewGroup, ChangeBounds())
                            menu.expand()
                        }
                    }
                }
            }

            appBarLayout.addOnOffsetChangedListener(onOffsetChangedListener)
        }
    }

    fun onPageChanged(id: Int) {
        when(id) {
            R.id.nav_home -> {
                binding.titleText.text = applicationContext.getText(R.string.app_name)
                binding.statusText.text = applicationContext.getText(R.string.app_version)
                //projectManager.removeOnStateChangedListener(T)
            }
            R.id.nav_projects -> {
                binding.titleText.text = applicationContext.getText(R.string.title_projects)
                binding.statusText.text = getString(R.string.subtitle_projects).format(0)
            }
            R.id.nav_plugins -> {
                val count = pluginManager.getPlugins().size
                binding.titleText.text = applicationContext.getText(R.string.title_plugins)
                binding.statusText.text = getString(R.string.subtitle_plugins).format(count)
                //projectManager.removeOnStateChangedListener(T)
            }
            R.id.nav_settings -> {
                binding.titleText.text = applicationContext.getText(R.string.title_settings)
                binding.statusText.text = ""
                //projectManager.removeOnStateChangedListener(T)
            }
        }
    }
}