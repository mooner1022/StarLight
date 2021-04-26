package com.mooner.starlight

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.SubMenu
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
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
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.utils.Utils.Companion.getLogger
import com.mooner.starlight.core.BackgroundTask
import com.mooner.starlight.plugincore.Session.Companion.getLanguageManager
import com.mooner.starlight.plugincore.Session.Companion.getProjectLoader
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
        private lateinit var fab: FabDialog
        private lateinit var view: View
        private lateinit var fabAnim: Animation
        private var runnable: Runnable? = null
        private var isShowingFabAnimation: Boolean = false

        fun reloadText() {
            val active = getProjectLoader().getEnabledProjects()
            runningBotsTextView.text = if (active.isEmpty()) "작동중인 봇이 없어요." else "${active.size}개의 봇이 작동중이에요."
        }

        fun setToolbarText(text: String) {
            ctr.title = text
        }

        fun showSnackbar(text: String, length: Int = Snackbar.LENGTH_LONG) {
            Snackbar.make(view, text, length).show()
            ObjectAnimator.ofFloat(fab, "translationY", -200f).apply {
                duration = 200
                start()
            }
            isShowingFabAnimation = true
            runnable = Runnable {
                ObjectAnimator.ofFloat(fab, "translationY", 0f).apply {
                    duration = 200
                    start()
                }
                isShowingFabAnimation = false
            }
            Handler(Looper.getMainLooper()).postDelayed(runnable!!, 2900)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

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
        fabAnim = AnimationUtils.loadAnimation(applicationContext, R.anim.fab_snackbar_anim)
        view = rootView
        fab = fabDialog

        with(fabDialog) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
            setTitle("새 프로젝트")
            setDialogCornerRadius(100f)
            setContentView(R.layout.dialog_new_project)
            setCanceledOnTouchOutside(true)
            setDialogIcon(R.drawable.ic_round_projects_24)
            setPositiveButton("추가") {
                val nameEditText = findViewById<EditText>(R.id.editTextNewProjectName)
                val projectName = nameEditText.text.toString()
                if (getProjectLoader().getProject(projectName) != null) {
                    nameEditText.error = "이미 존재하는 이름이에요."
                    nameEditText.requestFocus()
                    return@setPositiveButton
                }
                if (!"(^[1-9A-Za-z]+\$)".toRegex().matches(projectName)) {
                    nameEditText.error = "이름은 숫자와 영문자만 가능해요."
                    nameEditText.requestFocus()
                    return@setPositiveButton
                }
                val selectedLang = getLanguageManager().getLanguages()[languageSpinner.selectedIndex]
                getProjectLoader().newProject {
                    name = projectName
                    mainScript = "$projectName.${selectedLang.fileExtension}"
                    language = selectedLang.id
                    listeners = mutableListOf("default")
                }
                fabDialog.collapseDialog()
            }
            setNegativeButton("취소") {
                fabDialog.collapseDialog()
            }
            setOnClickListener { _ ->
                if (isShowingFabAnimation) {
                    if (runnable != null) Handler(Looper.getMainLooper()).removeCallbacks(runnable!!)
                    ObjectAnimator.ofFloat(view, "translationY", 0f).apply {
                        duration = 0
                        start()
                    }
                    isShowingFabAnimation = false
                }
                fabDialog.expandDialog()
                val nameEditText = findViewById<EditText>(R.id.editTextNewProjectName)
                nameEditText.text.clear()
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