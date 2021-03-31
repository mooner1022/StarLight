package com.mooner.starlight

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Environment
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
import com.google.android.material.navigation.NavigationView
import com.mooner.starlight.core.BackgroundTask
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.plugin.PluginLoader
import com.mooner.starlight.plugincore.project.Languages
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.plugincore.project.ProjectConfig
import org.angmarch.views.NiceSpinner
import java.io.File
import kotlin.math.abs


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            MODE_PRIVATE
        )

        if (!BackgroundTask.isRunning) {
            val intent = Intent(this, BackgroundTask::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        PluginLoader().loadPlugins()

        val dialog_fab: FabDialog = findViewById(R.id.dialog_fab)
        with(dialog_fab) {
            setTitle("새 프로젝트")
            setContentView(R.layout.dialog_new_project)
            setCanceledOnTouchOutside(true)
            setDialogIcon(R.drawable.ic_round_projects_24)
            setPositiveButton("추가") {
                val projectDir = File(Environment.getExternalStorageDirectory(), "StarLight/projects/")
                val projectName = findViewById<EditText>(R.id.editTextNewProjectName).text.toString()

                dialog_fab.collapseDialog()
            }
            setNegativeButton("취소") {
                dialog_fab.collapseDialog()
            }
            setOnClickListener { view ->
                dialog_fab.expandDialog()
                val languageSpinner = findDialogViewById(R.id.spinnerLanguage) as NiceSpinner
                val objects = Languages.values().map { it.name_kr }.toList()
                with(languageSpinner) {
                    setBackgroundColor(resources.getColor(R.color.transparent))
                    attachDataSource(objects)
                    setOnSpinnerItemSelectedListener { parent, view, position, id ->
                        Session.logger.i(javaClass.name, "Spinner item selected: $position")
                    }
                }
            }
        }

        val runningBotsText = findViewById<TextView>(R.id.textViewBotsRunning)
        val appBar = findViewById<AppBarLayout>(R.id.appBarLayout)
        appBar.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                val percent = abs(
                    verticalOffset / appBarLayout.totalScrollRange
                        .toFloat()
                )
                runningBotsText.alpha = 1.0f - percent
            }
        )

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