/*
 * ProblemSolverActivity.kt created by Minki Moon(mooner1022) on 8/4/23, 1:15 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings.solver

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.mooner.starlight.databinding.ActivityProblemSolverBinding

class ProblemSolverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProblemSolverBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProblemSolverBinding.inflate(layoutInflater)
        setContentView(binding.root)


    }
}