package com.mooner.starlight

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.bitvale.fabdialog.widget.FabDialog
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.navigation.NavigationView
import com.mooner.starlight.Utils.Companion.getLogger
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.core.ApplicationSession.projectLoader
import com.mooner.starlight.core.BackgroundTask
import com.mooner.starlight.plugincore.Session.Companion.getLanguageManager
import kotlinx.android.synthetic.main.app_bar_main.*
import org.angmarch.views.NiceSpinner
import kotlin.math.abs

@SuppressLint("StaticFieldLeak")
class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var languageSpinner: NiceSpinner

    companion object {
        private lateinit var runningBotsTextView: TextView
        private lateinit var ctr: CollapsingToolbarLayout

        fun reloadText() {
            val active = projectLoader.getEnabledProjects()
            runningBotsTextView.text = if (active.isEmpty()) "작동중인 봇이 없어요." else "${active.size}개의 봇이 작동중이에요."
        }

        fun setToolbarText(text: String) {
            ctr.title = text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        ctr = findViewById(R.id.collapsingToolbarLayout)
        ApplicationSession.context = applicationContext

        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            MODE_PRIVATE
        )

        println("isRunning: ${BackgroundTask.isRunning}")
        if (!BackgroundTask.isRunning) {
            println("start service")
            val intent = Intent(this, BackgroundTask::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        val fabDialog: FabDialog = findViewById(R.id.dialog_fab)
        with(fabDialog) {
            setTitle("새 프로젝트")
            setContentView(R.layout.dialog_new_project)
            setCanceledOnTouchOutside(true)
            setDialogIcon(R.drawable.ic_round_projects_24)
            setPositiveButton("추가") {
                val projectName = findViewById<EditText>(R.id.editTextNewProjectName).text.toString()
                projectLoader.newProject {
                    name = projectName
                    mainScript = "$projectName.js"
                    language = getLanguageManager().getLanguages()[languageSpinner.selectedIndex].id
                    listeners = mutableListOf("default")
                }
                fabDialog.collapseDialog()
            }
            setNegativeButton("취소") {
                fabDialog.collapseDialog()
            }
            setOnClickListener { _ ->
                fabDialog.expandDialog()
                languageSpinner = findDialogViewById(R.id.spinnerLanguage) as NiceSpinner
                val objects = getLanguageManager().getLanguages().map { it.name }.toList()
                with(languageSpinner) {
                    setBackgroundColor(context.getColor(R.color.transparent))
                    attachDataSource(objects)
                    setOnSpinnerItemSelectedListener { _, _, position, _ ->
                        getLogger().i(javaClass.name, "Spinner item selected: $position")
                    }
                }
            }
        }

        runningBotsTextView = findViewById(R.id.textViewBotsRunning)
        appBarLayout.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                val percent = abs(
                    verticalOffset / appBarLayout.totalScrollRange
                        .toFloat()
                )
                runningBotsTextView.alpha = 1.0f - percent
            }
        )
        reloadText()

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_projects, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}