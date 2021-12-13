package com.mooner.starlight

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.mooner.starlight.core.ForegroundTask
import com.mooner.starlight.databinding.ActivityMainBinding
import com.mooner.starlight.plugincore.Session.pluginManager
import com.mooner.starlight.plugincore.Session.projectManager
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.ui.ViewPagerAdapter
import com.mooner.starlight.utils.Utils.Companion.formatStringRes
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val T = "MainActivity"
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

        binding.viewPager.adapter = ViewPagerAdapter(this)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
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
        })

        val bottomBar = binding.bottomBar
        bottomBar.onItemSelectedListener = { _, item, _ ->
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

        binding.appBarLayout.addOnOffsetChangedListener(
                AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                    val percent = 1.0f - abs(
                            verticalOffset / appBarLayout.totalScrollRange
                                    .toFloat()
                    )
                    binding.statusText.alpha = percent
                    binding.titleText.alpha = percent
                    binding.imageViewLogo.alpha = percent
                }
        )
    }

    fun onPageChanged(id: Int) {
        when(id) {
            R.id.nav_home -> {
                binding.titleText.text = applicationContext.getText(R.string.app_name)
                binding.statusText.text = applicationContext.getText(R.string.app_version)
                projectManager.removeOnStateChangedListener(T)
            }
            R.id.nav_projects -> {
                binding.titleText.text = applicationContext.getText(R.string.title_projects)

                fun updateCount() {
                    val count = projectManager.getProjects().count { it.info.isEnabled }
                    binding.statusText.text = applicationContext.formatStringRes(
                        R.string.subtitle_projects,
                        mapOf(
                            "count" to count.toString()
                        )
                    )
                }
                projectManager.addOnStateChangedListener(T) {
                    runOnUiThread {
                        updateCount()
                    }
                }
                updateCount()
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
                projectManager.removeOnStateChangedListener(T)
            }
            R.id.nav_settings -> {
                binding.titleText.text = applicationContext.getText(R.string.title_settings)
                binding.statusText.text = ""
                projectManager.removeOnStateChangedListener(T)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}