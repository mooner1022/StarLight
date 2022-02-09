/*
 * MainActivity.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import dev.mooner.starlight.core.ForegroundTask
import dev.mooner.starlight.databinding.ActivityMainBinding
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.ui.ViewPagerAdapter
import dev.mooner.starlight.utils.formatStringRes
import github.com.st235.lib_expandablebottombar.MenuItem
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        private const val T = "MainActivity"
    }

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
            binding.bottomBar.menu.select(id)
            onPageChanged(id)
        }
    }
    private val onMenuItemSelectedListener = { _: View, item: MenuItem, _: Boolean ->
        val index = when(item.id) {
            R.id.nav_home -> 0
            R.id.nav_projects -> 1
            R.id.nav_plugins -> 2
            R.id.nav_settings -> 3
            else -> 0
        }
        binding.viewPager.setCurrentItem(index, true)
        onPageChanged(item.id)
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

        if (!ForegroundTask.isRunning) {
            Logger.v(T, "Starting foreground task...")
            val intent = Intent(this, ForegroundTask::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        binding.apply {
            viewPager.adapter = ViewPagerAdapter(this@MainActivity)
            viewPager.registerOnPageChangeCallback(onPageChangeCallback)
            bottomBar.onItemSelectedListener = onMenuItemSelectedListener
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
            }
            R.id.nav_plugins -> {
                val count = pluginManager.getPlugins().size
                binding.titleText.text = applicationContext.getText(R.string.title_plugins)
                binding.statusText.text = applicationContext.formatStringRes(
                    R.string.subtitle_plugins,
                    mapOf(
                        "count" to count.toString()
                    )
                )
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