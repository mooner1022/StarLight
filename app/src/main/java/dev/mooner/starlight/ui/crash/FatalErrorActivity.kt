package dev.mooner.starlight.ui.crash

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import dev.mooner.starlight.databinding.ActivityFatalErrorBinding
import dev.mooner.starlight.plugincore.utils.getInternalDirectory
import dev.mooner.starlight.utils.restartApplication
import java.io.File

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

        binding.share.setOnClickListener {
            ShareCompat.IntentBuilder(this).apply {
                setType("text/plain")
                setText(errMsg)
                setChooserTitle("에러 로그 공유")
            }.startChooser()
        }

        binding.exit.setOnClickListener {
            finish()
        }

        binding.restart.setOnClickListener {
            File(getInternalDirectory(), "STARTUP.info").also { file ->
                if (file.exists())
                    file.delete()
            }
            restartApplication()
        }
    }
}