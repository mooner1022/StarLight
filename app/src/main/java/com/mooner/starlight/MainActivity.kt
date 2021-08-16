package com.mooner.starlight

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.viewpager2.widget.ViewPager2
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.appbar.AppBarLayout
import com.mooner.starlight.core.ForegroundTask
import com.mooner.starlight.databinding.ActivityMainBinding
import com.mooner.starlight.plugincore.core.Session.Companion.getLanguageManager
import com.mooner.starlight.plugincore.core.Session.Companion.pluginLoader
import com.mooner.starlight.plugincore.core.Session.Companion.projectLoader
import com.mooner.starlight.plugincore.logger.LogType
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.ui.ViewPagerAdapter
import com.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import com.mooner.starlight.utils.Utils
import org.angmarch.views.NiceSpinner
import kotlin.math.abs

@SuppressLint("StaticFieldLeak")
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
            println("start service")
            val intent = Intent(this, ForegroundTask::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        binding.fabNewProject.setOnClickListener { _ ->
            binding.fabNewProject.hide()
            MaterialDialog(this@MainActivity, BottomSheet(LayoutMode.WRAP_CONTENT)).show {

                cornerRadius(25f)
                customView(R.layout.dialog_new_project)
                cancelOnTouchOutside(true)
                noAutoDismiss()

                val nameEditText = findViewById<EditText>(R.id.editTextNewProjectName)
                val languageSpinner: NiceSpinner = findViewById(R.id.spinnerLanguage)
                val objects = getLanguageManager().getLanguages().map { it.name }.toList()
                nameEditText.text.clear()
                with(languageSpinner) {
                    setBackgroundColor(context.getColor(R.color.transparent))
                    attachDataSource(objects)
                }

                positiveButton(text = "추가") {
                    val projectName = nameEditText.text.toString()
                    if (projectLoader.getProject(projectName) != null) {
                        nameEditText.error = "이미 존재하는 이름이에요."
                        nameEditText.requestFocus()
                        return@positiveButton
                    }
                    if (!"(^[-_0-9A-Za-z]+\$)".toRegex().matches(projectName)) {
                        nameEditText.error = "이름은 숫자와 -, _, 영문자만 가능해요."
                        nameEditText.requestFocus()
                        return@positiveButton
                    }
                    val selectedLang = getLanguageManager().getLanguages()[languageSpinner.selectedIndex]
                    projectLoader.newProject {
                        name = projectName
                        mainScript = "$projectName.${selectedLang.fileExtension}"
                        language = selectedLang.id
                        createdMillis = System.currentTimeMillis()
                        listeners = mutableListOf("default")
                    }
                    it.dismiss()
                }
                negativeButton(text = "취소") {
                    it.dismiss()
                }
                onDismiss {
                    binding.fabNewProject.show()
                }
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


        val logs = Logger.filterNot(LogType.DEBUG)
        val logsAdapter = LogsRecyclerViewAdapter(applicationContext)
        if (logs.isNotEmpty()) {
            logsAdapter.data = logs.toMutableList()
            logsAdapter.notifyItemRangeInserted(0, logs.size)
        }

        Logger.bindListener(T) {
            if (it.type != LogType.DEBUG) {
                logsAdapter.pushLog(it)
            }
        }
    }

    fun onPageChanged(id: Int) {
        when(id) {
            R.id.nav_home -> {
                binding.titleText.text = applicationContext.getText(R.string.app_name)
                binding.statusText.text = applicationContext.getText(R.string.app_version)
                updateFab(isShown = false)
            }
            R.id.nav_projects -> {
                val count = projectLoader.getProjects().count { it.config.isEnabled }
                binding.titleText.text = applicationContext.getText(R.string.title_projects)
                binding.statusText.text = Utils.formatStringRes(
                    R.string.subtitle_projects,
                    mapOf(
                        "count" to count.toString()
                    )
                )
                updateFab(isShown = true)
            }
            R.id.nav_plugins -> {
                val count = pluginLoader.getPlugins().size
                binding.titleText.text = applicationContext.getText(R.string.title_plugins)
                binding.statusText.text = Utils.formatStringRes(
                    R.string.subtitle_plugins,
                    mapOf(
                        "count" to count.toString()
                    )
                )
                updateFab(isShown = false)
            }
        }
    }

    private fun updateFab(isShown: Boolean) {
        if (isShown) {
            if (binding.fabNewProject.isOrWillBeHidden) {
                binding.fabNewProject.show()
            }
        } else {
            if (binding.fabNewProject.isOrWillBeShown) {
                binding.fabNewProject.hide()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.unbindListener(T)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}