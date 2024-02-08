package dev.mooner.starlight.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.load
import coil.request.repeatCount
import dev.mooner.starlight.MainActivity
import dev.mooner.starlight.R
import dev.mooner.starlight.core.ApplicationSession
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.databinding.ActivitySplashBinding
import dev.mooner.starlight.event.ApplicationEvent
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.ui.splash.quickstart.QuickStartActivity
import dev.mooner.starlight.ui.splash.quickstart.steps.SetPermissionFragment
import dev.mooner.starlight.utils.checkPermissions
import dev.mooner.starlight.utils.restartApplication
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LOG = LoggerFactory.logger {  }

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private var startupJob: Job? = null
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        bindLogNotifier()

        val pref = getSharedPreferences("general", 0)
        val isInitial = pref.getBoolean("isFirstOpen", true)
        if (isInitial) pref.edit {
            putBoolean("isFirstOpen", false)
        }

        val isPermissionsGrant = checkPermissions(SetPermissionFragment.REQUIRED_PERMISSIONS)

        val imageLoader = ImageLoader.Builder(applicationContext)
            .components {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
        Coil.setImageLoader(imageLoader)

        binding.splashAnimImageView.load(R.drawable.splash_anim) { repeatCount(0) }

        LOG.verbose {
            """
                TEST_QUICK_START= $TEST_QUICK_START
                isInitial= $isInitial
                isPermissionsGrant= $isPermissionsGrant
            """.trimIndent()
        }

        if (TEST_QUICK_START || isInitial || !isPermissionsGrant) {
            startActivity(Intent(this, QuickStartActivity::class.java))
        } else {
            init()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun init() {
        val initMillis = System.currentTimeMillis()

        val restartJob = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            delay(7000L)
            when(ApplicationSession.initState) {
                ApplicationSession.InitState.None ->
                    restartApplication()
                ApplicationSession.InitState.Done -> {
                    if (lifecycle.currentState == Lifecycle.State.STARTED)
                        startApplication(initMillis)
                }
                else -> {}
            }
        }

        startupJob = lifecycleScope.launchWhenCreated {
            if (GlobalApplication.isStartupAborted)
                return@launchWhenCreated
            if (ApplicationSession.isInitComplete) {
                startApplication(initMillis)
            } else {
                restartJob.start()
                EventHandler.on<ApplicationEvent.Session.StageUpdate>(this, replay = 3) {
                    if (restartJob.isActive)
                        restartJob.cancel()
                    value?.let {
                        println("------------------------ $value")
                        LOG.info { value }
                        runOnUiThread {
                            binding.textViewLoadStatus.text = "✦ $value"
                        }
                    } ?: startApplication(initMillis)
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
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    companion object {

        private const val MIN_LOAD_TIME = 1500L
        private const val ANIMATION_DURATION = 5000L

        private const val TEST_QUICK_START = false
    }
}