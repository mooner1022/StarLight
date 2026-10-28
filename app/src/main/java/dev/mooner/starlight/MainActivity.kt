/*
 * MainActivity.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import dev.mooner.starlight.databinding.ActivityMainBinding
import dev.mooner.starlight.diagnostics.Diagnostics
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.utils.hasFlag
import dev.mooner.starlight.ui.ViewPagerAdapter
import dev.mooner.starlight.ui.plugins.pluginsSubtitle
import dev.mooner.starlight.ui.settings.update.AppUpdater
import dev.mooner.starlight.ui.settings.update.UpdateCheckResult
import dev.mooner.starlight.ui.settings.update.checkForUpdate
import dev.mooner.starlight.ui.settings.update.isAutoUpdateCheckEnabled
import dev.mooner.starlight.utils.LAYOUT_TABLET
import dev.mooner.starlight.utils.applyEdgeToEdge
import dev.mooner.starlight.utils.layoutMode
import dev.mooner.starlight.utils.showChangelogDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    private lateinit var updater: AppUpdater
    private var currentTabId: Int = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        updater = AppUpdater(this)
        applyEdgeToEdge(binding.root) { _, _ ->
            window.navigationBarColor = ContextCompat.getColor(this, R.color.cardview)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        bindLogNotifier { log ->
            !log.message.startsWith("ECOMF: ") && !(log.flags hasFlag LogData.FLAG_COMPILE_RESULT)
        }

        binding.apply {
            viewPager.adapter = ViewPagerAdapter(this@MainActivity)
            viewPager.registerOnPageChangeCallback(getOnPageChangeCallback())
            viewPager.getChildAt(0).defaultFocusHighlightEnabled = false

            bottomMenu.setItemSelected(R.id.nav_home, isSelected = true)
            bottomMenu.setOnItemSelectedListener(::handleMenuItemSelection)
            if (layoutMode == LAYOUT_TABLET) {
                buttonExpandMenu?.setOnClickListener {
                    bottomMenu.let { menu ->
                        TransitionManager.beginDelayedTransition(root as ViewGroup, ChangeBounds())
                        if (menu.isExpanded())
                            menu.collapse()
                        else
                            menu.expand()
                    }
                }
            }

            appBarLayout.addOnOffsetChangedListener(::handleOffsetUpdate)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                EventHandler.on<Events.Config.GlobalConfigUpdate>(this) {
                    withContext(Dispatchers.Main) {
                        if (currentTabId == R.id.nav_home)
                            onPageChanged(currentTabId)
                    }
                }
            }
        }

        val prefs = getSharedPreferences("general", 0)
        val lastVersionCode = prefs.getInt("lastVersionCode", 0)
        if (lastVersionCode < BuildConfig.VERSION_CODE) {
            showChangelogDialog("StarLight 업데이트 완료!")
            prefs.edit {
                putInt("lastVersionCode", BuildConfig.VERSION_CODE)
            }
        } else if (savedInstanceState == null) {
            checkUpdateOnStart()
        }
    }

    private fun checkUpdateOnStart() {
        if (isUpdateChecked || !isAutoUpdateCheckEnabled)
            return
        isUpdateChecked = true
        lifecycleScope.launch {
            val update = checkForUpdate() as? UpdateCheckResult.Available ?: return@launch
            withStarted {
                updater.showUpdateDialog(update)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentTabId == R.id.nav_home)
            onPageChanged(currentTabId)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentTabId == R.id.nav_home)
            super.onBackPressed()
        else
            setCurrentTab(R.id.nav_home)
    }

    private fun setCurrentTab(id: Int) {
        val index = when(id) {
            R.id.nav_home     -> 0
            R.id.nav_projects -> 1
            R.id.nav_plugins  -> 2
            R.id.nav_logs     -> 3
            R.id.nav_settings -> 4
            else              -> 0
        }
        binding.apply {
            viewPager.setCurrentItem(index, true)
            bottomMenu.setItemSelected(id, true)
        }
        onPageChanged(id)
    }

    private fun getOnPageChangeCallback() =
        object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val id = when(position) {
                    0 -> R.id.nav_home
                    1 -> R.id.nav_projects
                    2 -> R.id.nav_plugins
                    3 -> R.id.nav_logs
                    4 -> R.id.nav_settings
                    else -> R.id.nav_home
                }
                binding.bottomMenu.setItemSelected(id, true)
                onPageChanged(id)
            }
        }

    private fun handleMenuItemSelection(id: Int) {
        val index = when(id) {
            R.id.nav_home     -> 0
            R.id.nav_projects -> 1
            R.id.nav_plugins  -> 2
            R.id.nav_logs     -> 3
            R.id.nav_settings -> 4
            else              -> 0
        }
        binding.viewPager.setCurrentItem(index, true)
        onPageChanged(id)
    }

    private fun handleOffsetUpdate(appBarLayout: AppBarLayout, verticalOffset: Int) {
        val alpha = 1.0f - abs(
            verticalOffset / appBarLayout.totalScrollRange.toFloat()
        )
        binding.apply {
            statusText.alpha = alpha
            titleText.alpha = alpha
            imageViewLogo.alpha = alpha
        }
    }

    fun onPageChanged(id: Int) {
        val (title, status) = with(applicationContext) {
            when(id) {
                R.id.nav_home ->
                    getText(R.string.app_name) to
                            (Diagnostics.powerWarning(this) ?: getText(R.string.app_version))

                R.id.nav_projects ->
                    getText(R.string.title_projects) to
                            getString(R.string.subtitle_projects)
                                .format(Session.projectManager.getEnabledProjects().size)

                R.id.nav_plugins ->
                    getText(R.string.title_plugins) to
                            pluginsSubtitle()

                R.id.nav_logs ->
                    getString(R.string.title_logs) to ""

                R.id.nav_settings ->
                    applicationContext.getText(R.string.title_settings) to ""

                else ->
                    null to null
            }
        }

        binding.titleText.text  = title
        binding.statusText.text = status
        currentTabId = id
    }

    companion object {
        private var isUpdateChecked = false
    }
}
