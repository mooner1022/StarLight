package com.mooner.starlight.ui.debugroom

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.mooner.starlight.R
import com.mooner.starlight.databinding.ActivityDebugRoomSettingsBinding

class DebugRoomSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugRoomSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugRoomSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)


    }
}