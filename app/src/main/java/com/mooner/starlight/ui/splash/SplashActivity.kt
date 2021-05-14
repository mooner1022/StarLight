package com.mooner.starlight.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.databinding.ActivitySplashBinding
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.logger.LogType
import com.mooner.starlight.utils.Alert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.concurrent.schedule

class SplashActivity : AppCompatActivity() {
    companion object {
        private const val MIN_LOAD_TIME = 1500L
    }
    private var spinTimer: Timer? = null
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        var isSpinning = false
        var clickCnt = 0
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
                                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                                finish()
                            }
                        }
                    } else {
                        if (spinTimer != null) {
                            spinTimer!!.cancel()
                        }
                        runOnUiThread {
                            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                            finish()
                        }
                    }
                }
            )
        }

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