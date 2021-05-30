package com.mooner.starlight

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.core.content.res.ResourcesCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.mooner.starlight.plugincore.theme.ThemeManager
import com.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import com.mooner.starlight.ui.overlay.OverlayService
import com.mooner.starlight.utils.Utils
import com.skydoves.needs.NeedsAnimation
import com.skydoves.needs.NeedsItem
import com.skydoves.needs.createNeeds
import com.skydoves.needs.showNeeds
import jp.wasabeef.recyclerview.animators.FadeInLeftAnimator
import org.angmarch.views.NiceSpinner
import www.sanju.motiontoast.MotionToast
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
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
        )

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

    @SuppressLint("Range")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        windowContext = this
        rootLayout = binding.innerLayout.rootLayout
        ctr = findViewById(R.id.collapsingToolbarLayout)

        val isInitial = intent.getBooleanExtra("isInitial", true)
        if (isInitial || !Utils.checkPermissions(this, REQUIRED_PERMISSIONS)) {
            val needs = createNeeds(this) {
                setTitleIconResource(R.mipmap.ic_logo)
                title = "시작하기에 앞서,\nStarLight를 사용하기 위해 아래 권한들이 필요해요!"
                addNeedsItem(
                    NeedsItem(
                        null,
                        "· 저장소 쓰기",
                        "(필수)",
                        "기기의 파일에 접근하여 데이터를 저장할 수 있어요."
                    )
                )
                addNeedsItem(
                    NeedsItem(
                        null,
                        "· 저장소 읽기",
                        "(필수)",
                        "기기의 파일에 접근하여 데이터를 읽을 수 있어요."
                    )
                )
                addNeedsItem(
                    NeedsItem(
                        null,
                        "· 인터넷",
                        "(필수)",
                        "인터넷에 접속할 수 있어요."
                    )
                )
                description = "위 권한들은 필수 권한으로,\n허용하지 않을 시 앱이 정상적으로 동작하지 않아요."
                confirm = "승인"
                backgroundAlpha = 0.6f
                needsAnimation = NeedsAnimation.FADE
            }
            needs.setOnConfirmListener {
                if (Utils.checkPermissions(this@MainActivity, REQUIRED_PERMISSIONS)) {
                    needs.dismiss()
                    MotionToast.darkColorToast(this@MainActivity,
                        "권한 승인!",
                        "앱을 사용할 준비가 되었어요! ٩(*•̀ᴗ•́*)و",
                        MotionToast.TOAST_SUCCESS,
                        MotionToast.GRAVITY_BOTTOM,
                        MotionToast.LONG_DURATION,
                        ResourcesCompat.getFont(this@MainActivity, R.font.nanumsquare_round_regular))
                } else {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        REQUIRED_PERMISSIONS,
                        MODE_PRIVATE
                    )
                    ActivityCompat.OnRequestPermissionsResultCallback { _, permissions, grantResults ->
                        if (permissions.contentEquals(REQUIRED_PERMISSIONS)) {
                            if ((grantResults.isNotEmpty() &&
                                        grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                                needs.dismiss()
                                MotionToast.darkColorToast(this@MainActivity,
                                    "권한 승인!",
                                    "앱을 사용할 준비가 되었어요! ٩(*•̀ᴗ•́*)و",
                                    MotionToast.TOAST_SUCCESS,
                                    MotionToast.GRAVITY_BOTTOM,
                                    MotionToast.LONG_DURATION,
                                    ResourcesCompat.getFont(this@MainActivity,R.font.nanumsquare_round_regular))
                            } else {
                                MotionToast.darkColorToast(this@MainActivity,
                                    "권한 승인 실패",
                                    "권한이 승인되지 않았어요.. (´•ω•̥`)",
                                    MotionToast.TOAST_ERROR,
                                    MotionToast.GRAVITY_BOTTOM,
                                    MotionToast.LONG_DURATION,
                                    ResourcesCompat.getFont(this@MainActivity,R.font.nanumsquare_round_regular))
                            }
                        }
                    }
                }
            }
            binding.drawerLayout.showNeeds(needs)
        }

        ThemeManager.matchBackgroundColor(applicationContext,
            mapOf(
                ThemeManager.COLOR_TOOLBAR to arrayOf(
                    binding.innerLayout.toolbar,
                    binding.innerLayout.collapsingToolbarLayout
                )
            )
        )

        println("isRunning: ${ForegroundTask.isRunning}")
        if (!ForegroundTask.isRunning) {
            println("start service")
            val intent = Intent(this, ForegroundTask::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

        val bottomSheet = binding.bottomSheet
        val logs = Logger.filterNot(LogType.DEBUG)
        val logsAdapter = LogsRecyclerViewAdapter(applicationContext)
        if (logs.isNotEmpty()) {
            val recyclerLayoutManager = LinearLayoutManager(applicationContext).apply {
                reverseLayout = true
                stackFromEnd = true
            }
            logsAdapter.data = logs.toMutableList()
            with(bottomSheet.recyclerViewLogs) {
                itemAnimator = FadeInLeftAnimator()
                layoutManager = recyclerLayoutManager
                adapter = logsAdapter
            }
            logsAdapter.notifyItemRangeInserted(0, logs.size)
        }

        Logger.bindListener {
            if (it.type != LogType.DEBUG) {
                logsAdapter.pushLog(it)
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

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