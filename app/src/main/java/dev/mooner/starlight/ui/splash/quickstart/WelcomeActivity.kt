/*
 * WelcomeActivity.kt created by Minki Moon(mooner1022) on 6/14/24, 8:17 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.splash.quickstart

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.mooner.starlight.databinding.ActivityWelcomeBinding
import dev.mooner.starlight.utils.applyEdgeToEdge

class WelcomeActivity: AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root)

        arrayOf(binding.buttonNext, binding.labelNext).forEach {
            it.setOnClickListener {
                startActivity(Intent(this, QuickStartActivity::class.java))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
                else
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        }
    }
}