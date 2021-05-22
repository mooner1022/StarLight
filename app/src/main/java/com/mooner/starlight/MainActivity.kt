package com.mooner.starlight

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.SubMenu
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.core.ForegroundTask
import com.mooner.starlight.databinding.ActivityMainBinding
import com.mooner.starlight.plugincore.Session.Companion.getLanguageManager
import com.mooner.starlight.plugincore.Session.Companion.getProjectLoader
import com.mooner.starlight.plugincore.logger.LogType
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.utils.Alert
import org.angmarch.views.NiceSpinner
import kotlin.math.abs

@SuppressLint("StaticFieldLeak")
class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var languageSpinner: NiceSpinner
    private lateinit var binding: ActivityMainBinding

    companion object {
        private lateinit var textViewStatus: TextView
        private lateinit var ctr: CollapsingToolbarLayout
        lateinit var fab: FloatingActionButton
        private lateinit var fabAnim: Animation
        private lateinit var rootLayout: CoordinatorLayout
        lateinit var windowContext: Context

        fun reloadText(text: String? = null) {
            if (text == null) {
                val active = getProjectLoader().getEnabledProjects()
                textViewStatus.text = if (active.isEmpty()) "작동중인 봇이 없어요." else "${active.size}개의 봇이 작동중이에요."
            } else {
                textViewStatus.text = text
            }
        }

        fun setToolbarText(text: String) {
            ctr.title = text
        }

        fun showSnackbar(text: String, duration: Int = Snackbar.LENGTH_SHORT) {
            Snackbar.make(rootLayout, text, duration).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        windowContext = this
        Logger.bindListener {
            if (it.type == LogType.ERROR) {
                Alert.show(Alert.DIALOG, this,"ERROR", it.message)
            }
        }
        rootLayout = binding.innerLayout.rootLayout
        ctr = findViewById(R.id.collapsingToolbarLayout)
        ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.INTERNET,
                ),
                MODE_PRIVATE
        )

        println("isRunning: ${ForegroundTask.isRunning}")
        if (!ForegroundTask.isRunning) {
            println("start service")
            val intent = Intent(this, ForegroundTask::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        fabAnim = AnimationUtils.loadAnimation(applicationContext, R.anim.fab_snackbar_anim)
        fab = findViewById(R.id.fabNewProject)

        with(fab) {
            setOnClickListener { _ ->
                fab.hide()
                MaterialDialog(this@MainActivity, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                    //window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
                    cornerRadius(25f)
                    customView(R.layout.dialog_new_project)
                    cancelOnTouchOutside(true)
                    noAutoDismiss()
                    positiveButton(text = "추가") {
                        val nameEditText = it.findViewById<EditText>(R.id.editTextNewProjectName)
                        val projectName = nameEditText.text.toString()
                        if (getProjectLoader().getProject(projectName) != null) {
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
                        getProjectLoader().newProject {
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
                        fab.show()
                    }

                    val nameEditText = findViewById<EditText>(R.id.editTextNewProjectName)
                    nameEditText.text.clear()
                    languageSpinner = findViewById(R.id.spinnerLanguage)
                    val objects = getLanguageManager().getLanguages().map { it.name }.toList()
                    with(languageSpinner) {
                        setBackgroundColor(context.getColor(R.color.transparent))
                        attachDataSource(objects)
                        setOnSpinnerItemSelectedListener { _, _, position, _ ->
                            Logger.i(javaClass.name, "Spinner item selected: $position")
                        }
                    }
                }
            }
        }

        textViewStatus = findViewById(R.id.statusView)
        binding.innerLayout.appBarLayout.addOnOffsetChangedListener(
                AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                    val percent = 1.0f - abs(
                            verticalOffset / appBarLayout.totalScrollRange
                                    .toFloat()
                    )
                    textViewStatus.alpha = percent
                    binding.innerLayout.imageViewLogo.alpha = percent
                }
        )
        reloadText()

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val m: Menu = navView.menu
        val menuGroup: SubMenu = m.addSubMenu("플러그인")
        menuGroup.add("Foo")
        menuGroup.add("Bar")
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
                setOf(
                        R.id.nav_home, R.id.nav_projects, R.id.nav_plugins, R.id.nav_slideshow
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