package com.mooner.starlight.ui.splash

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.databinding.ActivitySplashBinding
import com.mooner.starlight.utils.Utils
import com.skydoves.needs.NeedsAnimation
import com.skydoves.needs.NeedsItem
import com.skydoves.needs.createNeeds
import com.skydoves.needs.showNeeds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import www.sanju.motiontoast.MotionToast
import java.util.*
import kotlin.concurrent.schedule

class SplashActivity : AppCompatActivity() {
    companion object {
        private const val MIN_LOAD_TIME = 1500L
    }
    private var spinTimer: Timer? = null
    private lateinit var binding: ActivitySplashBinding
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.INTERNET,
    )

    @SuppressLint("Range")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val pref = getSharedPreferences("general", 0)
        val isInitial = pref.getBoolean("isInitial", true)
        if (isInitial) pref.edit().putBoolean("isInitial", false).apply()
        val intent = Intent(this@SplashActivity, MainActivity::class.java)
        intent.putExtra("isInitial", isInitial)

        fun init() {
            val initMillis = System.currentTimeMillis()

            CoroutineScope(Dispatchers.Default).launch {
                ApplicationSession.context = applicationContext
                ApplicationSession.init(
                    {
                        runOnUiThread {
                            binding.textViewLoadStatus.text = it
                        }
                    },
                    {
                        val currentMillis = System.currentTimeMillis()
                        println("time: ${currentMillis - initMillis}")
                        if ((currentMillis - initMillis) <= MIN_LOAD_TIME) {
                            Timer().schedule(MIN_LOAD_TIME - (currentMillis - initMillis)) {
                                if (spinTimer != null) {
                                    spinTimer!!.cancel()
                                }
                                runOnUiThread {
                                    startActivity(intent)
                                    finish()
                                }
                            }
                        } else {
                            if (spinTimer != null) {
                                spinTimer!!.cancel()
                            }
                            runOnUiThread {
                                startActivity(intent)
                                finish()
                            }
                        }
                    }
                )
            }
        }

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
                if (Utils.checkPermissions(this, REQUIRED_PERMISSIONS)) {
                    needs.dismiss()
                    MotionToast.darkColorToast(this,
                        "권한 승인!",
                        "앱을 사용할 준비가 되었어요! ٩(*•̀ᴗ•́*)و",
                        MotionToast.TOAST_SUCCESS,
                        MotionToast.GRAVITY_BOTTOM,
                        MotionToast.LONG_DURATION,
                        ResourcesCompat.getFont(this, R.font.nanumsquare_round_regular))
                } else {
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
                                MotionToast.darkColorToast(this,
                                    "권한 승인!",
                                    "앱을 사용할 준비가 되었어요! ٩(*•̀ᴗ•́*)و",
                                    MotionToast.TOAST_SUCCESS,
                                    MotionToast.GRAVITY_BOTTOM,
                                    MotionToast.LONG_DURATION,
                                    ResourcesCompat.getFont(this,R.font.nanumsquare_round_regular))
                                init()
                            } else {
                                MotionToast.darkColorToast(this,
                                    "권한 승인 실패",
                                    "권한이 승인되지 않았어요.. (´•ω•̥`)",
                                    MotionToast.TOAST_ERROR,
                                    MotionToast.GRAVITY_BOTTOM,
                                    MotionToast.LONG_DURATION,
                                    ResourcesCompat.getFont(this,R.font.nanumsquare_round_regular))
                            }
                        }
                    }
                }
            }
            binding.root.showNeeds(needs)
        } else {
            init()
        }

        var isSpinning = false
        var clickCnt = 0

        val anim = AnimationUtils.loadAnimation(applicationContext, R.anim.splash_logo_anim).apply {
            fillAfter = true
        }
        val spinAnim = AnimationUtils.loadAnimation(applicationContext, R.anim.splash_spin_anim).apply {
            fillAfter = true
        }

        binding.imageViewSplashLogo.startAnimation(anim)

        binding.imageViewSplashLogo.setOnClickListener {
            clickCnt++
            if (!isSpinning && clickCnt >= 4) {
                isSpinning = true
                binding.imageViewSplashLogo.startAnimation(spinAnim)
                spinTimer = Timer().apply {
                    schedule(500) {
                        isSpinning = false
                    }
                }
            }
        }
    }
}