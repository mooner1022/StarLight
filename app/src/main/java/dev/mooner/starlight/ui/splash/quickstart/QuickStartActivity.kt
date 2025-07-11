/*
 * QuickStartActivity.kt created by Minki Moon(mooner1022) on 22. 2. 5. 오후 2:43
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.splash.quickstart

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.AnimBuilder
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import dev.mooner.starlight.BuildConfig
import dev.mooner.starlight.PREF_IS_INITIAL
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityQuickStartBinding
import dev.mooner.starlight.utils.applyEdgeToEdge
import dev.mooner.starlight.utils.restartApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QuickStartActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityQuickStartBinding
    private var navController: NavController? = null
    private var position = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityQuickStartBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root)

        val navHostFrag: NavHostFragment = supportFragmentManager.findFragmentById(R.id.frame_stepper) as NavHostFragment
        val controller = navHostFrag.navController
        navController = controller

        binding.buttonPrev.setOnClickListener(this)
        binding.buttonNext.setOnClickListener(this)
    }

    @SuppressLint("SetTextI18n")
    override fun onClick(v: View) {
        val id = v.id
        when (id) {
            R.id.buttonPrev -> {
                if (position <= 0)
                    return
                position--

                if (position == 1)
                    binding.textButtonNext.text = "✦ 다음"
            }
            R.id.buttonNext -> {
                when (position) {
                    2 -> return finishQuickStart()
                    1 -> binding.textButtonNext.text = "✦ 완료"
                }
                position++
            }
            else -> return
        }
        binding.stepIndicator.text = "${position + 1} | 3"
        
        val tabId = indexToTabId(position)
        navController!!.navigateWithAnimation(tabId) {
            if (id == R.id.buttonNext) {
                enter = R.anim.slide_in_left
                exit = R.anim.activity_fade_out
            } else {
                enter = R.anim.activity_fade_in
                exit = R.anim.slide_out_right
            }
        }
    }

    fun setNextButtonEnabled(enabled: Boolean) {
        binding.buttonNext.isEnabled = enabled
    }

    fun setPrevButtonEnabled(enabled: Boolean) {
        binding.buttonPrev.isEnabled = enabled
    }

    private fun finishQuickStart() {
        val prefs = getSharedPreferences("general", 0)
        prefs.edit(commit = true) {
            putBoolean(PREF_IS_INITIAL, false)
            putInt("lastVersionCode", BuildConfig.VERSION_CODE)
        }

        Toast.makeText(this, "설정 완료, StarLight 사용을 환영합니다!", Toast.LENGTH_LONG).show()

        lifecycleScope.launch {
            delay(200)
            restartApplication()
        }
    }

    @IdRes
    private fun indexToTabId(index: Int): Int =
        when(index) {
            0 -> R.id.step_1_dest
            1 -> R.id.step_2_dest
            2 -> R.id.step_3_dest
            else -> R.id.step_1_dest
        }

    private fun NavController.navigateWithAnimation(
        @IdRes resId: Int,
        navAnimBuilder: AnimBuilder.() -> Unit
    ) {
        val navOptions = navOptions {
            anim(navAnimBuilder)
        }
        navigate(resId, null, navOptions)
    }
}