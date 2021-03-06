package dev.mooner.starlight.ui.splash

import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.load
import coil.request.repeatCount
import dev.mooner.starlight.MainActivity
import dev.mooner.starlight.R
import dev.mooner.starlight.core.GlobalApplication.Companion.REQUIRED_PERMISSIONS
import dev.mooner.starlight.core.session.ApplicationSession
import dev.mooner.starlight.databinding.ActivitySplashBinding
import dev.mooner.starlight.event.SessionStageUpdateEvent
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.ui.crash.FatalErrorActivity
import dev.mooner.starlight.ui.splash.quickstart.QuickStartActivity
import dev.mooner.starlight.utils.checkPermissions
import dev.mooner.starlight.utils.getInternalDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import java.io.File

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val pref = getSharedPreferences("general", 0)
        val isInitial = pref.getBoolean("isInitial", true)
        if (isInitial) pref.edit {
            putBoolean("isInitial", false)
        }

        val isPermissionsGrant = checkPermissions(REQUIRED_PERMISSIONS)

        val imageLoader = ImageLoader.Builder(applicationContext)
            .componentRegistry {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder(applicationContext))
                } else {
                    add(GifDecoder())
                }
            }
            .build()
        Coil.setImageLoader(imageLoader)

        binding.splashAnimImageView.load(R.drawable.splash_anim) { repeatCount(0) }

        //Logger.v("TEST_QUICK_START= $TEST_QUICK_START\nisInitial= $isInitial\nisPermissionsGrant= $isPermissionsGrant")
        if (TEST_QUICK_START || isInitial || !isPermissionsGrant) {
            startActivity(Intent(this, QuickStartActivity::class.java))
        } else {
            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            init(intent)
        }
    }

    private fun init(intent: Intent) {
        val startupFile = File(getInternalDirectory(), "STARTUP.info")
        if (startupFile.exists() && startupFile.isFile) {
            val startupData: Map<String, String> = Session.json.decodeFromString(startupFile.readText())
            if (startupData.containsKey("last_error")) {
                val errorIntent = Intent(this, FatalErrorActivity::class.java).apply {
                    putExtra("errorMessage", startupData["last_error"])
                }
                startupFile.delete()
                startActivity(errorIntent)
                finish()
                return
            }
        }

        val initMillis = System.currentTimeMillis()

        //val webview = WebView(applicationContext)
        //webview.loadUrl(EditorActivity.ENTRY_POINT)

        lifecycleScope.launchWhenCreated {
            if (Session.isInitComplete) {
                launch(Dispatchers.Default) {
                    startApplication(initMillis, intent)
                }
            } else {
                Session.eventManager.on<SessionStageUpdateEvent>(this) {
                    value?.let {
                        Logger.i(T, value)
                        runOnUiThread {
                            binding.textViewLoadStatus.text = value
                        }
                    } ?: startApplication(initMillis, intent)
                }
            }
        }
    }

    private suspend fun startApplication(initMillis: Long, intent: Intent) {
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
        private val T = SplashActivity::class.simpleName!!

        private const val MIN_LOAD_TIME = 1500L
        private const val ANIMATION_DURATION = 5000L

        private const val TEST_QUICK_START = false
    }
}