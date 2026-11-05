package dev.mooner.starlight.ui.crash

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.view.isVisible
import dev.mooner.starlight.R
import dev.mooner.starlight.core.CrashGuard
import dev.mooner.starlight.databinding.ActivityFatalErrorBinding
import dev.mooner.starlight.utils.fitSystemBars
import dev.mooner.starlight.utils.restartApplication

/**
 * Shows a crash report. When the same crash kept coming back the app didn't start, and this is where the user chooses
 * to start anyway; otherwise the app already restarted and this only shows what happened.
 */
class FatalErrorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFatalErrorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFatalErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitSystemBars(binding.root)

        val errMsg: String = intent.getStringExtra(EXTRA_REPORT) ?: error("Failed to retrieve error data")
        val blocked = intent.getBooleanExtra(EXTRA_BLOCKED, true)

        binding.headline.setText(if (blocked) R.string.crash_blocked_title else R.string.crash_recovered_title)
        binding.errorLog.apply {
            text = errMsg
            movementMethod = ScrollingMovementMethod()
        }

        binding.share.setOnClickListener {
            ShareCompat.IntentBuilder(this).apply {
                setType("text/plain")
                setText(errMsg)
                setChooserTitle(R.string.crash_share_title)
            }.startChooser()
        }

        // The app is running already; closing the report is all there is to do.
        binding.exit.setText(if (blocked) R.string.crash_exit else R.string.close)
        binding.exit.setOnClickListener {
            finish()
        }

        binding.restart.isVisible = blocked
        binding.restart.setOnClickListener {
            CrashGuard.dismiss()
            restartApplication()
        }
    }

    companion object {
        private const val EXTRA_REPORT = "errorMessage"
        private const val EXTRA_BLOCKED = "blocked"

        /** [blocked]: the app didn't start because of this crash. */
        fun intent(context: Context, report: String, blocked: Boolean): Intent =
            Intent(context, FatalErrorActivity::class.java)
                .putExtra(EXTRA_REPORT, report)
                .putExtra(EXTRA_BLOCKED, blocked)

        fun shareIntent(report: String): Intent =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, report)
    }
}
