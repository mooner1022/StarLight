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
import dev.mooner.starlight.databinding.ActivitySplashBinding
import dev.mooner.starlight.event.ApplicationEvent
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.ui.splash.quickstart.WelcomeActivity
import dev.mooner.starlight.ui.splash.quickstart.steps.SetPermissionFragment
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
        val isPermissionsGrant = checkPermissions(SetPermissionFragment.REQUIRED_PERMISSIONS)

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
            startActivity(Intent(this, WelcomeActivity::class.java))
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        else
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    companion object {

        private const val MIN_LOAD_TIME = 1300L
        private const val ANIMATION_DURATION = 5000L

        private const val TEST_QUICK_START = false
    }
}