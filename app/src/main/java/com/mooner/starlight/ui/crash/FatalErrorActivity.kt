package com.mooner.starlight.ui.crash

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import com.mooner.starlight.databinding.ActivityFatalErrorBinding
import com.mooner.starlight.utils.restartApplication

class FatalErrorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFatalErrorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFatalErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val errMsg: String = intent.getStringExtra("errorMessage")?: error("Failed to retrieve error data")

        binding.errorLog.apply {
            text = errMsg
            movementMethod = ScrollingMovementMethod()
        }

        binding.restart.setOnClickListener {
            restartApplication(this)
        }

        binding.share.setOnClickListener {
            ShareCompat.IntentBuilder(this).apply {
                setType("text/plain")
                setText(errMsg)
                setChooserTitle("에러 로그 공유")
            }.startChooser()
        }
    }
}