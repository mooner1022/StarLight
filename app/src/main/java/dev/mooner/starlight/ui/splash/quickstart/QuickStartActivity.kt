/*
 * QuickStartActivity.kt created by Minki Moon(mooner1022) on 22. 2. 5. 오후 2:43
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.splash.quickstart

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityQuickStartBinding
import dev.mooner.starlight.utils.restartApplication

class QuickStartActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityQuickStartBinding
    private var position = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFrag: NavHostFragment = supportFragmentManager.findFragmentById(R.id.frame_stepper) as NavHostFragment
        val controller = navHostFrag.navController

        with(binding) {
            stepper.setupWithNavController(controller)
            buttonPrevious.setOnClickListener(this@QuickStartActivity)
            buttonNext.setOnClickListener(this@QuickStartActivity)
            buttonFinish.setOnClickListener(this@QuickStartActivity)
        }
    }

    override fun onClick(view: View) {
        when(view.id) {
            R.id.button_previous -> {
                binding.stepper.goToPreviousStep()
                updateButton(--position)
            }
            R.id.button_next -> {
                binding.stepper.goToNextStep()
                updateButton(++position)
            }
            R.id.button_finish -> {
                Toast.makeText(this, "설정이 완료되었어요! 앱을 재시작할게요!", Toast.LENGTH_LONG).show()
                restartApplication()
            }
        }
    }

    fun showButton(buttonType: Buttons) {
        when(buttonType) {
            Buttons.Previous -> binding.buttonPrevious
            Buttons.Next -> binding.buttonNext
            Buttons.Finish -> binding.buttonFinish
        }.let {
            when(it) {
                is FloatingActionButton -> {
                    if (it.isOrWillBeHidden)
                        it.show()
                }
                is ExtendedFloatingActionButton -> {
                    it.show()
                }
            }
        }
    }

    fun hideButton(buttonType: Buttons) {
        when(buttonType) {
            Buttons.Previous -> binding.buttonPrevious
            Buttons.Next -> binding.buttonNext
            Buttons.Finish -> binding.buttonFinish
        }.let {
            when(it) {
                is FloatingActionButton -> {
                    if (it.isOrWillBeShown)
                        it.hide()
                }
                is ExtendedFloatingActionButton -> {
                    it.hide()
                }
            }
        }
    }

    private fun updateButton(position: Int) {
        when(position) {
            0 -> hideButton(Buttons.Previous)
            else -> {
                showButton(Buttons.Previous)
                showButton(Buttons.Next)
            }
        }
    }

    sealed class Buttons {
        data object Previous: Buttons()
        data object Next: Buttons()
        data object Finish: Buttons()
    }
}