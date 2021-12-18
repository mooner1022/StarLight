package com.mooner.starlight.ui.splash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.load
import coil.request.repeatCount
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.core.session.ApplicationSession
import com.mooner.starlight.core.session.SessionInitListener
import com.mooner.starlight.databinding.ActivitySplashBinding
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.ui.crash.FatalErrorActivity
import com.mooner.starlight.utils.FileUtils
import com.mooner.starlight.utils.checkPermissions
import com.skydoves.needs.NeedsAnimation
import com.skydoves.needs.NeedsItem
import com.skydoves.needs.createNeeds
import com.skydoves.needs.showNeeds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.*
import kotlin.concurrent.schedule

class SplashActivity : AppCompatActivity() {

    companion object {
        private val T = SplashActivity::class.simpleName!!

        private const val MIN_LOAD_TIME = 2500L
        private const val ANIMATION_DURATION = 5000L
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
        )
    }
    private lateinit var binding: ActivitySplashBinding
    private var loadTimer: Timer? = null

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
        val intent = Intent(this@SplashActivity, MainActivity::class.java)
        intent.putExtra("isInitial", isInitial)

        if (isInitial || !checkPermissions(this, REQUIRED_PERMISSIONS)) {
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
                needsAnimation = NeedsAnimation.FADE
            }
            needs.setOnConfirmListener {
                ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    MODE_PRIVATE
                )
                ActivityCompat.OnRequestPermissionsResultCallback { _, permissions, grantResults ->
                    if (permissions.contentEquals(REQUIRED_PERMISSIONS)) {
                        if ((grantResults.isNotEmpty() &&
                                    grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                            needs.dismiss()
                            Snackbar.make(view, "앱을 사용할 준비가 되었어요! ٩(*•̀ᴗ•́*)و", Snackbar.LENGTH_LONG).show()
                            init(intent)
                        } else {
                            Snackbar.make(view, "권한이 승인되지 않았어요.. (´•ω•̥`)و", Snackbar.LENGTH_LONG).show()
                            init(intent)
                        }
                    }
                }
            }
            binding.root.showNeeds(needs)
        } else {
            init(intent)
        }

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

        binding.splashAnimImageView.load(R.drawable.splash_anim) {
            repeatCount(0)
        }
    }

    private fun init(intent: Intent) {
        val startupFile = File(FileUtils.getInternalDirectory(), "STARTUP.info")
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

        CoroutineScope(Dispatchers.Default).launch {
            ApplicationSession.init(applicationContext)
        }

        ApplicationSession.setOnInitListener(object : SessionInitListener {
            override fun onPhaseChanged(phase: String) {
                Logger.i(T, phase)
                runOnUiThread {
                    binding.textViewLoadStatus.text = phase
                }
            }

            override fun onFinished() {
                val currentMillis = System.currentTimeMillis()
                if ((currentMillis - initMillis) <= MIN_LOAD_TIME) {
                    val delay = if (!ApplicationSession.isInitComplete)
                        ANIMATION_DURATION - (currentMillis - initMillis)
                    else
                        MIN_LOAD_TIME - (currentMillis - initMillis)

                    loadTimer = Timer().apply {
                        schedule(delay) {
                            loadTimer = null
                            startMainActivity(intent)
                        }
                    }
                } else {
                    startMainActivity(intent)
                }
            }
        })
    }

    private fun startMainActivity(intent: Intent) = runOnUiThread {
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (loadTimer != null) {
            loadTimer!!.cancel()
            loadTimer = null
        }
    }
}