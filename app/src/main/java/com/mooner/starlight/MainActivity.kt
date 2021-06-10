package com.mooner.starlight

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.ResourcesCompat
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager.widget.ViewPager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.core.ForegroundTask
import com.mooner.starlight.databinding.ActivityMainBinding
import com.mooner.starlight.plugincore.core.Session.Companion.getLanguageManager
import com.mooner.starlight.plugincore.core.Session.Companion.projectLoader
import com.mooner.starlight.plugincore.logger.LogType
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import devlight.io.library.ntb.NavigationTabBar
import jp.wasabeef.recyclerview.animators.FadeInLeftAnimator
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
                val active = projectLoader.getEnabledProjects()
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
        //val toolbar: Toolbar = findViewById(R.id.toolbar)
        //setSupportActionBar(toolbar)

        windowContext = this
        rootLayout = binding.innerLayout.rootLayout
        ctr = findViewById(R.id.collapsingToolbarLayout)

        //val isInitial = intent.getBooleanExtra("isInitial", true)

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

        /*
        Blurry.with(applicationContext)
            .radius(10)
            .sampling(8)
            .color(Color.parseColor("#FFFFFFFF"))
            .postOnto(binding.bottomSheet.bottomSheetLogs)
         */

        binding.bottomSheet.tabNavView.setOnNavigationItemSelectedListener {
            Navigation.findNavController(binding.root).navigate(it.itemId)
            true
        }

        val dp80 = dpToPx(100.0f)
        val dp40 = dpToPx(40.0f)
        val maxWidth = resources.displayMetrics.widthPixels - dp40
        val maxHeight = resources.displayMetrics.heightPixels - dpToPx(120.0f)
        println("maxHeight= $maxHeight")
        val bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet.bottomSheetLogs)
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {

                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    //binding.bottomSheet.constraintLayout.alpha = 1.0f - (slideOffset / 8)
                    val params = binding.bottomSheet.constraintLayout.layoutParams
                    params.height = (dp80 + (maxHeight * slideOffset)).toInt()
                    params.width = (maxWidth + (dp40 * slideOffset)).toInt()
                    bottomSheet.findViewById<ConstraintLayout>(R.id.constraintLayout).layoutParams = params
                    bottomSheet.requestLayout()
                    bottomSheet.forceLayout()
                }
            }
        )

        textViewStatus = findViewById(R.id.statusView)
        binding.innerLayout.appBarLayout.addOnOffsetChangedListener(
                AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                    val percent = 1.0f - abs(
                            verticalOffset / appBarLayout.totalScrollRange
                                    .toFloat()
                    )
                    textViewStatus.alpha = percent
                    binding.innerLayout.textViewTitle.alpha = percent
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
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun dpToPx(dp: Float): Float = dp * (resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)

    private fun pxToDp(px: Float): Float = px / (resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
}