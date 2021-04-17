package com.mooner.starlight.ui.splash

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.animation.AnimationUtils
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.plugincore.Session
import kotlinx.android.synthetic.main.activity_splash.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.concurrent.schedule

class SplashActivity : AppCompatActivity() {
    private var spinTimer: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        var isSpinning = false
        var clickCnt = 0
        val initMillis = System.currentTimeMillis()

        CoroutineScope(Dispatchers.Default).launch {
            ApplicationSession.context = applicationContext
            ApplicationSession.init(
                {
                    runOnUiThread {
                        textViewLoadStatus.text = it
                    }
                },
                {
                    val currentMillis = System.currentTimeMillis()
                    println("time: ${currentMillis - initMillis}")
                    if ((currentMillis - initMillis) <= 2000) {
                        Timer().schedule(2000 - (currentMillis - initMillis)) {
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

        cardViewLogoImage.startAnimation(anim)

        cardViewLogoImage.setOnClickListener {
            clickCnt++
            if (!isSpinning && clickCnt >= 4) {
                isSpinning = true
                cardViewLogoImage.startAnimation(spinAnim)
                spinTimer = Timer().apply {
                    schedule(500) {
                        isSpinning = false
                    }
                }
            }
        }
    }
}