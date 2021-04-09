package com.mooner.starlight.ui.splash

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.animation.AnimationUtils
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import kotlinx.android.synthetic.main.activity_splash.*
import java.util.*
import kotlin.concurrent.schedule

class SplashActivity : AppCompatActivity() {
    private var spinTimer: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        var isSpinning = false
        var clickCnt = 0

        val anim = AnimationUtils.loadAnimation(applicationContext, R.anim.splash_logo_anim).apply {
            fillAfter = true
        }
        val spinAnim = AnimationUtils.loadAnimation(applicationContext, R.anim.splash_spin_anim).apply {
            fillAfter = true
        }

        cardViewLogoImage.startAnimation(anim)
        Timer().schedule(2000) {
            if (spinTimer != null) {
                spinTimer!!.cancel()
            }
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }

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