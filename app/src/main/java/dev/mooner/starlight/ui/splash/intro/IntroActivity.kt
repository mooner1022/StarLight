package dev.mooner.starlight.ui.splash.intro

import android.Manifest.permission.*
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import dev.mooner.starlight.BuildConfig
import dev.mooner.starlight.PREF_IS_INITIAL
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityIntroBinding
import dev.mooner.starlight.databinding.IntroPageDoneBinding
import dev.mooner.starlight.databinding.IntroPageListBinding
import dev.mooner.starlight.databinding.IntroPageWelcomeBinding
import dev.mooner.starlight.databinding.ItemIntroRowBinding
import dev.mooner.starlight.databinding.ItemIntroSummaryBinding
import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.utils.applyEdgeToEdge
import dev.mooner.starlight.utils.canPostNotification
import dev.mooner.starlight.utils.checkPermissions
import dev.mooner.starlight.utils.requestManageStoragePermission
import dev.mooner.starlight.utils.restartApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import dev.mooner.configdsl.R as ConfigR

class IntroActivity : AppCompatActivity() {

    private class PermissionRow(
        val binding: ItemIntroRowBinding,
        @StringRes val title: Int,
        val isRequired: Boolean,
        val check: () -> Boolean,
    ) {
        var isGranted: Boolean? = null
    }

    private lateinit var binding: ActivityIntroBinding
    private lateinit var welcome: IntroPageWelcomeBinding
    private lateinit var done: IntroPageDoneBinding
    private val pages = mutableListOf<View>()
    private val rows = mutableListOf<PermissionRow>()

    private var page = -1
    private var firstPage = PAGE_WELCOME
    private var isStorageRequested = false
    private val requestedPermissions = mutableSetOf<String>()

    private var skyAnimator: ValueAnimator? = null
    private var haloAnimator: ValueAnimator? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshRows(animate = true)
    }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = showPage(page - 1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.content)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        onBackPressedDispatcher.addCallback(this, backCallback)
        binding.starField.density = 0.35f
        binding.starField.brightness = 0.6f

        firstPage = if (intent.getBooleanExtra(EXTRA_PERMISSIONS_ONLY, false)) PAGE_REQUIRED else PAGE_WELCOME
        buildPages()
        binding.indicator.count = pages.size - firstPage
        binding.buttonPrev.setOnClickListener { showPage(page - 1) }
        binding.buttonNext.setOnClickListener { onNext() }

        refreshRows(animate = false)
        showPage(savedInstanceState?.getInt(STATE_PAGE, firstPage) ?: firstPage)
        startHalo()
    }

    override fun onResume() {
        super.onResume()
        if (isStorageRequested) {
            isStorageRequested = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() && !checkPermissions(REQUIRED_PERMISSIONS))
                requestStorage()
        }
        refreshRows(animate = true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, page)
    }

    override fun onDestroy() {
        skyAnimator?.cancel()
        haloAnimator?.cancel()
        super.onDestroy()
    }

    private fun buildPages() {
        val container = binding.pageContainer
        welcome = IntroPageWelcomeBinding.inflate(layoutInflater, container, false)
        done = IntroPageDoneBinding.inflate(layoutInflater, container, false)

        val tour = listPage(R.string.intro_tour_eyebrow, R.string.intro_tour_title, R.string.intro_tour_subtitle)
        tour.pageContent.addRow(R.drawable.ic_round_insert_comment_24, R.string.intro_feature_reply_title, R.string.intro_feature_reply_desc)
        tour.pageContent.addRow(R.drawable.ic_round_code_24, R.string.intro_feature_project_title, R.string.intro_feature_project_desc)
        tour.pageContent.addRow(R.drawable.ic_round_plugins_24, R.string.intro_feature_plugin_title, R.string.intro_feature_plugin_desc)
        tour.pageContent.addRow(R.drawable.ic_round_view_day_24, R.string.intro_feature_home_title, R.string.intro_feature_home_desc)

        val required = listPage(R.string.intro_required_eyebrow, R.string.intro_required_title, R.string.intro_required_subtitle)
        required.pageContent.addPermission(R.drawable.ic_round_sd_storage_24, R.string.intro_perm_storage, R.string.intro_perm_storage_desc, isRequired = true,
            check = { checkPermissions(REQUIRED_PERMISSIONS) }, request = ::requestStorage)
        required.pageContent.addPermission(ConfigR.drawable.ic_round_notifications_active_24, R.string.intro_perm_listener, R.string.intro_perm_listener_desc, isRequired = true,
            check = { packageName in NotificationManagerCompat.getEnabledListenerPackages(this) }, request = ::requestListener)
        required.pageContent.addPermission(ConfigR.drawable.ic_round_layers_24, R.string.intro_perm_overlay, R.string.intro_perm_overlay_desc, isRequired = true,
            check = { Settings.canDrawOverlays(this) }, request = ::requestOverlay)

        val optional = listPage(R.string.intro_optional_eyebrow, R.string.intro_optional_title, R.string.intro_optional_subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            optional.pageContent.addPermission(R.drawable.ic_round_notifications_24, R.string.intro_perm_post, R.string.intro_perm_post_desc, isRequired = false,
                check = { canPostNotification() }, request = ::requestPostNotification)
        optional.pageContent.addPermission(ConfigR.drawable.ic_round_battery_saver_24, R.string.intro_perm_battery, R.string.intro_perm_battery_desc, isRequired = false,
            check = { getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(packageName) == true }, request = ::requestBatteryOptimization)

        pages += welcome.root
        pages += tour.root
        pages += required.root
        pages += optional.root
        pages += done.root
        for (view in pages) {
            view.isVisible = false
            container.addView(view)
        }
    }

    private fun listPage(@StringRes eyebrow: Int, @StringRes title: Int, @StringRes subtitle: Int): IntroPageListBinding =
        IntroPageListBinding.inflate(layoutInflater, binding.pageContainer, false).apply {
            this.eyebrow.setText(eyebrow)
            this.title.setText(title)
            this.subtitle.setText(subtitle)
        }

    private fun LinearLayout.addRow(@DrawableRes iconRes: Int, @StringRes titleRes: Int, @StringRes descriptionRes: Int): ItemIntroRowBinding =
        ItemIntroRowBinding.inflate(layoutInflater, this, true).apply {
            icon.setImageResource(iconRes)
            title.setText(titleRes)
            description.setText(descriptionRes)
            root.isClickable = false
        }

    private fun LinearLayout.addPermission(
        @DrawableRes iconRes: Int,
        @StringRes titleRes: Int,
        @StringRes descriptionRes: Int,
        isRequired: Boolean,
        check: () -> Boolean,
        request: () -> Unit,
    ) {
        val row = addRow(iconRes, titleRes, descriptionRes)
        row.trailing.isVisible = true
        val entry = PermissionRow(row, titleRes, isRequired, check)
        row.root.setOnClickListener {
            if (entry.isGranted != true)
                request()
        }
        rows += entry
    }

    private fun showPage(index: Int) {
        if (index !in firstPage until pages.size || index == page)
            return
        val previous = pages.getOrNull(page)
        val isFirst = page == -1
        val forward = index > page
        page = index
        backCallback.isEnabled = index > firstPage

        if (index == PAGE_DONE)
            buildSummary()
        previous?.let { exitPage(it, forward) }
        enterPage(pages[index], forward, isFirst)
        moveSky(animate = !isFirst)
        updateButtons(animate = !isFirst)
        if (index == PAGE_DONE)
            binding.root.postDelayed({
                if (page == PAGE_DONE)
                    binding.burst.burst(done.doneLogo)
            }, 520)
    }

    private fun enterPage(view: View, forward: Boolean, isFirst: Boolean) {
        view.animate().cancel()
        view.isVisible = true
        view.alpha = 1f
        view.translationX = 0f
        val content = view.findViewById<ViewGroup>(R.id.pageContent)
        val offset = dp(40f) * if (forward) 1 else -1
        content.children.forEachIndexed { i, child ->
            val isPop = child.tag == "pop"
            child.animate().cancel()
            child.alpha = 0f
            child.translationX = if (isFirst) 0f else offset
            child.translationY = if (isFirst) dp(24f) else 0f
            if (isPop) {
                child.scaleX = 0.6f
                child.scaleY = 0.6f
            }
            child.animate()
                .alpha(1f)
                .translationX(0f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay((if (isFirst) 150L else 90L) + i * 55L)
                .setDuration(if (isPop) 640L else 440L)
                .setInterpolator(if (isPop) OvershootInterpolator(1.6f) else DecelerateInterpolator(2f))
                .start()
        }
    }

    private fun exitPage(view: View, forward: Boolean) {
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationX(dp(48f) * if (forward) -1 else 1)
            .setStartDelay(0)
            .setDuration(220)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .withEndAction {
                if (pages.getOrNull(page) !== view)
                    view.isVisible = false
                view.alpha = 1f
                view.translationX = 0f
            }
            .start()
    }

    private fun moveSky(animate: Boolean) {
        val targetX = page * resources.displayMetrics.widthPixels * 0.5f
        val targetProgress = (page - firstPage).toFloat()
        skyAnimator?.cancel()
        if (!animate) {
            binding.starField.parallaxX = targetX
            binding.indicator.progress = targetProgress
            return
        }
        val fromX = binding.starField.parallaxX
        val fromProgress = binding.indicator.progress
        skyAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 820
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                val t = it.animatedValue as Float
                binding.starField.parallaxX = fromX + (targetX - fromX) * t
                binding.indicator.progress = fromProgress + (targetProgress - fromProgress) * t
            }
            start()
        }
    }

    private fun updateButtons(animate: Boolean) {
        val showPrev = page > firstPage
        if (binding.buttonPrev.isVisible != showPrev) {
            if (animate)
                TransitionManager.beginDelayedTransition(binding.buttonBar, AutoTransition().setDuration(260))
            binding.buttonPrev.isVisible = showPrev
        }

        val remaining = rows.count { it.isRequired && it.isGranted != true }
        val label = when {
            page == PAGE_WELCOME                    -> getString(R.string.intro_start)
            page == PAGE_REQUIRED && remaining > 0 -> getString(R.string.intro_remaining, remaining)
            page == PAGE_DONE                       -> getString(R.string.intro_finish)
            else                                    -> getString(R.string.intro_next)
        }
        val enabled = page != PAGE_REQUIRED || remaining == 0
        val wasEnabled = binding.buttonNext.isEnabled
        binding.buttonNext.isEnabled = enabled
        setNextLabel(label, animate)
        if (animate && enabled && !wasEnabled)
            binding.buttonNext.pop()
    }

    private fun setNextLabel(text: String, animate: Boolean) {
        val label = binding.textNext
        if (label.text == text)
            return
        label.animate().cancel()
        if (!animate || label.text.isNullOrEmpty()) {
            label.text = text
            label.alpha = 1f
            label.translationY = 0f
            return
        }
        val offset = dp(8f)
        label.animate()
            .alpha(0f)
            .translationY(-offset)
            .setStartDelay(0)
            .setDuration(110)
            .withEndAction {
                label.text = text
                label.translationY = offset
                label.animate().alpha(1f).translationY(0f).setDuration(170).start()
            }
            .start()
    }

    private fun View.pop() {
        animate().cancel()
        scaleX = 0.92f
        scaleY = 0.92f
        animate().scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(3f))
            .setDuration(420)
            .start()
    }

    private fun onNext() {
        when (page) {
            PAGE_DONE -> finishIntro()
            else      -> showPage(page + 1)
        }
    }

    private fun refreshRows(animate: Boolean) {
        for (row in rows) {
            val granted = row.check()
            val previous = row.isGranted
            if (previous == granted)
                continue
            row.isGranted = granted
            row.binding.setGranted(granted, animate && previous == false && granted)
        }
        if (page != -1)
            updateButtons(animate)
    }

    private fun ItemIntroRowBinding.setGranted(isGranted: Boolean, animate: Boolean) {
        allow.animate().cancel()
        granted.animate().cancel()
        allow.alpha = 1f
        allow.scaleX = 1f
        allow.scaleY = 1f
        if (!animate) {
            allow.isInvisible = isGranted
            granted.isInvisible = !isGranted
            granted.scaleX = 1f
            granted.scaleY = 1f
            granted.rotation = 0f
            return
        }
        allow.animate()
            .alpha(0f)
            .scaleX(0.7f)
            .scaleY(0.7f)
            .setDuration(150)
            .withEndAction {
                allow.isInvisible = true
                allow.alpha = 1f
                allow.scaleX = 1f
                allow.scaleY = 1f
            }
            .start()
        granted.isVisible = true
        granted.scaleX = 0f
        granted.scaleY = 0f
        granted.rotation = -120f
        granted.animate()
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .setStartDelay(90)
            .setDuration(460)
            .setInterpolator(OvershootInterpolator(2.6f))
            .start()
        granted.postDelayed({
            if (granted.isShown)
                binding.burst.burst(granted)
        }, 180)
    }

    private fun buildSummary() {
        val summary = done.summary
        summary.removeAllViews()
        for (row in rows) {
            val item = ItemIntroSummaryBinding.inflate(layoutInflater, summary, true)
            item.title.setText(row.title)
            if (row.isGranted == true) {
                item.state.setBackgroundResource(R.drawable.bg_intro_granted)
                item.state.setImageResource(ConfigR.drawable.ic_round_check_24)
                item.state.setColorFilter(getColor(R.color.store_on_gold))
            } else {
                item.state.setBackgroundResource(R.drawable.bg_intro_halo)
                item.state.setImageDrawable(null)
                item.note.isVisible = true
            }
        }
    }

    private fun startHalo() {
        haloAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                welcome.haloOuter.setHaloPhase(t)
                welcome.haloInner.setHaloPhase((t + 0.5f) % 1f)
                welcome.logo.translationY = sin(t * 2f * PI.toFloat()) * dp(4f)
            }
            start()
        }
    }

    private fun View.setHaloPhase(t: Float) {
        val scale = 0.7f + t * 0.7f
        scaleX = scale
        scaleY = scale
        alpha = (1f - t) * 0.8f
    }

    private fun finishIntro() {
        binding.buttonNext.isEnabled = false
        val prefs = getSharedPreferences("general", 0)
        prefs.edit(commit = true) {
            putBoolean(PREF_IS_INITIAL, false)
            if (!prefs.contains("lastVersionCode"))
                putInt("lastVersionCode", BuildConfig.VERSION_CODE)
        }
        binding.burst.burst(binding.buttonNext)
        lifecycleScope.launch {
            delay(520)
            restartApplication()
        }
    }

    private fun requestStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            isStorageRequested = true
            requestManageStoragePermission()
            return
        }
        val missing = REQUIRED_PERMISSIONS.filter { it != MANAGE_EXTERNAL_STORAGE && checkSelfPermission(it) != PERMISSION_GRANTED }
        requestRuntime(missing, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()))
    }

    private fun requestListener() {
        val component = ComponentName(this, NotificationListener::class.java).flattenToString()
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
        else
            null
        runCatching { startActivity(detail ?: Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            .onFailure { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    private fun requestOverlay() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri()))
    }

    private fun requestPostNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            return
        requestRuntime(
            listOf(POST_NOTIFICATIONS),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    @Suppress("BatteryLife")
    private fun requestBatteryOptimization() {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri()))
    }

    private fun requestRuntime(permissions: List<String>, fallback: Intent) {
        if (permissions.isEmpty())
            return
        val key = permissions.first()
        if (key in requestedPermissions && permissions.none(::shouldShowRequestPermissionRationale)) {
            startActivity(fallback)
            return
        }
        requestedPermissions += key
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun packageUri(): Uri =
        Uri.fromParts("package", packageName, null)

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    companion object {

        const val EXTRA_PERMISSIONS_ONLY = "permissionsOnly"

        private const val STATE_PAGE = "page"

        private const val PAGE_WELCOME  = 0
        private const val PAGE_REQUIRED = 2
        private const val PAGE_DONE     = 4

        val REQUIRED_PERMISSIONS = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                arrayOf(INTERNET, FOREGROUND_SERVICE, MANAGE_EXTERNAL_STORAGE)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                arrayOf(INTERNET, FOREGROUND_SERVICE, MANAGE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                arrayOf(INTERNET, FOREGROUND_SERVICE, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)
            else ->
                arrayOf(INTERNET, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)
        }
    }
}
