/*
 * DebugRoomActivity.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.debugroom

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityDebugRoomBinding

class DebugRoomActivity: AppCompatActivity() {

    private lateinit var binding: ActivityDebugRoomBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME)!!
        val fragment = DebugRoomFragment.newInstance(
            projectName = projectName,
            showLeave = true,
            fixedPadding = false
        )
        supportFragmentManager.beginTransaction().apply {
            replace(
                R.id.fragment_container_debug_room,
                fragment
            )
        }.commit()
    }

    companion object {

        const val EXTRA_PROJECT_NAME = "project_name"
    }
}