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
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.utils.fitPage
import dev.mooner.starlight.utils.restartIfNotReady

class DebugRoomActivity: AppCompatActivity() {

    private lateinit var binding: ActivityDebugRoomBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Brought back before the runtime started: start over from the splash, without restoring what needs it.
        if (restartIfNotReady()) {
            super.onCreate(null)
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        binding = ActivityDebugRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitPage(binding.root)
        bindLogNotifier()

        // Made anew, as on a rotation, the screen already has its room back, with what was being typed in it:
        // putting a new one in its place would throw that away.
        if (supportFragmentManager.findFragmentById(R.id.fragment_container_debug_room) != null)
            return
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