package dev.mooner.starlight.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.Coil
import coil.ImageLoader
import dev.mooner.starlight.MainActivity
import dev.mooner.starlight.PREF_IS_INITIAL
import dev.mooner.starlight.core.ApplicationSession
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.ui.crash.FatalErrorActivity
import dev.mooner.starlight.databinding.ActivitySplashBinding
import dev.mooner.starlight.event.ApplicationEvent
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.ui.splash.intro.IntroActivity
import dev.mooner.starlight.utils.PendingEntry
import dev.mooner.starlight.utils.applyEdgeToEdge
import dev.mooner.starlight.utils.checkPermissions
import dev.mooner.starlight.utils.restartApplication
import kotlinx.coroutines.*

private val LOG = LoggerFactory.logger {  }

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        // From the safe start notification: start again as usual.
        if (intent.getBooleanExtra(EXTRA_RESTART, false)) {
            super.onCreate(null)
            restartApplication()
            return
        }
        // The start was blocked after repeated crashes and nothing is coming for the splash to wait for. The report
        // that offers a way on was closed, or never shown if the process came up in the background: show it again.
        GlobalApplication.blockedReport?.let { report ->
            super.onCreate(null)
            // The one the process opened as it started is used again if it is still there, not covered by a second.
            startActivity(FatalErrorActivity.intent(this, report, blocked = true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
            return
        }
        super.onCreate(savedInstanceState)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures())
            // Apply the insets as padding to the view. Here, set all the dimensions
            // as appropriate to your layout. You can also update the view's margin if
            // more appropriate.
            view.updatePadding(insets.left, insets.top, insets.right, insets.bottom)

            // Return CONSUMED if you don't want the window insets to keep passing down
            // to descendant views.
            WindowInsetsCompat.CONSUMED
        }

        bindLogNotifier()

        val pref = getSharedPreferences("general", 0)
        val isInitial = pref.getBoolean(PREF_IS_INITIAL, true)
        val isPermissionsGrant = checkPermissions(IntroActivity.REQUIRED_PERMISSIONS)

        val imageLoader = ImageLoader.Builder(applicationContext)
            /*
            .components {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
             */
            .build()
        Coil.setImageLoader(imageLoader)

        LOG.verbose {
            """
                TEST_QUICK_START= $TEST_QUICK_START
                isInitial= $isInitial
                isPermissionsGrant= $isPermissionsGrant
            """.trimIndent()
        }

        if (TEST_QUICK_START || isInitial || !isPermissionsGrant) {
            //ConfigDSL.registerAdapterImpl(::ParentConfigAdapterImpl)
            // The runtime doesn't start from here: where the user was going is forgotten, not kept for another day.
            PendingEntry.take()
            startActivity(Intent(this, IntroActivity::class.java)
                .putExtra(IntroActivity.EXTRA_PERMISSIONS_ONLY, !TEST_QUICK_START && !isInitial))
            finish()
        } else {
            if (GlobalApplication.lastStageValue != null)
                binding.textViewLoadStatus.text = "✦ ${GlobalApplication.lastStageValue}"
            init()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun init() {
        val initMillis = System.currentTimeMillis()

        val restartJob = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            delay(7000L)
            when(ApplicationSession.initState) {
                Session.InitState.None ->
                    restartApplication()
                Session.InitState.Done -> {
                    if (lifecycle.currentState == Lifecycle.State.STARTED)
                        startApplication(initMillis)
                }
                else -> {}
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                if (GlobalApplication.isStartupAborted)
                    return@repeatOnLifecycle
                if (ApplicationSession.isInitComplete) {
                    startApplication(initMillis)
                } else {
                    restartJob.start()
                    EventHandler.on<ApplicationEvent.Session.StageUpdate>(this) {
                        if (value == null) {
                            startApplication(initMillis)
                            return@on
                        }
                        if (restartJob.isActive)
                            restartJob.cancel()
                        println("------------------------ $value")
                        LOG.info { value }
                        withContext(Dispatchers.Main) {
                            binding.textViewLoadStatus.text = "✦ $value"
                        }
                    }
                }
            }
        }
    }

    private suspend fun startApplication(initMillis: Long) {
        val intent = Intent(this@SplashActivity, MainActivity::class.java)
        val currentMillis = System.currentTimeMillis()
        if ((currentMillis - initMillis) <= MIN_LOAD_TIME) {
            val delay = if (!ApplicationSession.isInitComplete)
                ANIMATION_DURATION - (currentMillis - initMillis)
            else
                MIN_LOAD_TIME - (currentMillis - initMillis)

            delay(delay)
            startMainActivity(intent)
        } else {
            startMainActivity(intent)
        }
    }

    private fun startMainActivity(intent: Intent) = runOnUiThread {
        startActivity(intent)
        // The screen that sent the user here because the runtime wasn't up yet, on top of the main one.
        PendingEntry.take()?.let { entry ->
            runCatching { startActivity(entry) }
                .onFailure { LOG.warn { "Couldn't go on to ${entry.component?.shortClassName}: $it" } }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        else
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    companion object {

        const val EXTRA_RESTART = "restart"

        private const val MIN_LOAD_TIME = 1300L
        private const val ANIMATION_DURATION = 5000L

        private const val TEST_QUICK_START = false
    }
}