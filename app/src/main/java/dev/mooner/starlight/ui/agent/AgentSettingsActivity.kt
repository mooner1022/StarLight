package dev.mooner.starlight.ui.agent

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.mooner.starlight.agent.runtime.AgentNotifier
import kotlinx.coroutines.delay
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.ApprovalPolicy
import dev.mooner.starlight.agent.provider.ProviderKind
import dev.mooner.starlight.databinding.ActivityAgentSettingsBinding
import dev.mooner.starlight.databinding.ItemAgentOptionBinding
import dev.mooner.starlight.databinding.ItemAgentProviderBinding
import kotlinx.coroutines.launch

/** Providers to connect, and how the agent behaves. */
class AgentSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgentSettingsBinding
    private val hub by lazy { AgentHub.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgentSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        // The night header runs under the status bar, whose icons stay light on it.
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = getColor(R.color.agent_bg)
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) !=
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.headerContent.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            binding.content.updatePadding(bottom = maxOf(bars.bottom, ime.bottom) + AgentUi.dp(this, 28))
            insets
        }
        binding.stars.density = 0.4f
        binding.stars.brightness = 0.8f
        binding.back.setOnClickListener { finish() }

        setUpBehavior()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                hub.settings.version.collect {
                    renderProviders()
                    renderLiveUpdates()
                }
            }
        }
    }

    private fun renderProviders() {
        binding.providers.removeAllViews()
        val active = hub.settings.activeProvider()
        for (kind in ProviderKind.entries) {
            val row = ItemAgentProviderBinding.inflate(LayoutInflater.from(this), binding.providers, true)
            row.badge.text = kind.displayName.first().toString()
            row.name.text = kind.displayName
            val configured = hub.settings.isConfigured(kind)
            row.status.text = when {
                !configured -> getString(R.string.agent_provider_not_connected)
                kind == ProviderKind.CHATGPT && hub.chatGpt.email != null ->
                    getString(R.string.agent_chatgpt_signed_in, hub.chatGpt.email)
                else -> {
                    val model = hub.settings.model(kind)
                    listOfNotNull(getString(R.string.agent_provider_connected), model?.let { AgentUi.modelName(hub, kind, it) }).joinToString(" · ")
                }
            }
            row.inUse.isVisible = configured && kind == active
            row.root.setOnClickListener { ProviderSheet.show(this, kind) }
        }
    }

    private fun setUpBehavior() {
        val options = listOf(
            Triple(ApprovalPolicy.AUTO, R.string.agent_approval_auto, R.string.agent_approval_auto_desc),
            Triple(ApprovalPolicy.ASK_DESTRUCTIVE, R.string.agent_approval_destructive, R.string.agent_approval_destructive_desc),
            Triple(ApprovalPolicy.ASK_WRITES, R.string.agent_approval_writes, R.string.agent_approval_writes_desc),
        )
        val rows = options.map { (policy, title, description) ->
            ItemAgentOptionBinding.inflate(LayoutInflater.from(this), binding.approvals, true).apply {
                this.title.setText(title)
                this.description.setText(description)
                root.setOnClickListener {
                    hub.settings.approvalPolicy = policy
                    renderApproval(policy)
                }
            }
        }
        fun render(policy: ApprovalPolicy) = rows.forEachIndexed { i, row -> row.check.isVisible = options[i].first == policy }
        approvalRenderer = ::render
        render(hub.settings.approvalPolicy)

        val steps = hub.settings.maxSteps
        binding.maxSteps.progress = (steps / STEP_UNIT - 1).coerceIn(0, binding.maxSteps.max)
        binding.maxStepsValue.text = steps.toString()
        binding.maxSteps.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = (progress + 1) * STEP_UNIT
                binding.maxStepsValue.text = value.toString()
                if (fromUser)
                    hub.settings.maxSteps = value
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.notify.isChecked = hub.settings.notifyOnFinish
        binding.notifyRow.setOnClickListener {
            binding.notify.isChecked = !binding.notify.isChecked
            hub.settings.notifyOnFinish = binding.notify.isChecked
        }

        binding.liveUpdatesGroup.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
        binding.liveUpdates.isChecked = hub.settings.liveUpdates
        binding.liveUpdatesRow.setOnClickListener {
            binding.liveUpdates.isChecked = !binding.liveUpdates.isChecked
            hub.settings.liveUpdates = binding.liveUpdates.isChecked
            renderLiveUpdates()
        }
        FilePreview.shrinkIcons(this, binding.liveUpdatesTest)
        binding.liveUpdatesTest.setOnClickListener { testLiveUpdate() }

        binding.instructions.setText(hub.settings.customInstructions)
        binding.instructions.doAfterTextChanged { hub.settings.customInstructions = it?.toString().orEmpty() }
    }

    override fun onResume() {
        super.onResume()
        // The user may come back from the system's setting for it.
        renderLiveUpdates()
    }

    /** Whether the system lets the app show Live Updates, and whether the last run's progress showed as one. */
    private fun renderLiveUpdates() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA)
            return
        val allowed = getSystemService(NotificationManager::class.java)?.canPostPromotedNotifications() != false
        val status = binding.liveUpdatesStatus
        status.isVisible = hub.settings.liveUpdates
        status.setOnClickListener(null)
        status.isClickable = false
        val result = when {
            !allowed -> getString(R.string.agent_live_updates_blocked)
            else     -> when (hub.settings.lastLiveUpdatePromoted) {
                true  -> getString(R.string.agent_live_updates_shown)
                false -> getString(R.string.agent_live_updates_not_shown)
                null  -> getString(R.string.agent_live_updates_untried)
            }
        }
        // The detail line says where promotion failed, if it did; before any attempt, at least the Android version.
        status.text = listOf(
            result,
            hub.settings.lastLiveUpdateDetail ?: getString(R.string.agent_live_updates_version, androidVersion()),
        ).joinToString("\n\n")
        binding.liveUpdatesTest.isVisible = hub.settings.liveUpdates
        if (!allowed)
            status.setOnClickListener {
                runCatching {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }.onFailure {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }
            }
    }

    /**
     * Posts a sample of a run's progress notification, then reads back what the system did with it. The sample
     * stays for a while so the user can see where it shows (status bar chip, lock screen, Now Bar).
     */
    private fun testLiveUpdate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA)
            return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.agent_live_updates_test_no_permission, Toast.LENGTH_LONG).show()
            return
        }
        NotificationManagerCompat.from(this).notify(AgentNotifier.TEST_ID, AgentNotifier.liveUpdateTest(this))
        Toast.makeText(this, R.string.agent_live_updates_test_posted, Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            // The system applies the flag once it has processed the post.
            delay(TEST_CHECK_DELAY_MILLIS)
            val posted = getSystemService(NotificationManager::class.java)?.activeNotifications
                ?.firstOrNull { it.id == AgentNotifier.TEST_ID }?.notification ?: return@launch
            val (promoted, detail) = AgentNotifier.promotionReport(this@AgentSettingsActivity, posted)
            hub.settings.lastLiveUpdatePromoted = promoted
            hub.settings.lastLiveUpdateDetail = detail
            renderLiveUpdates()
        }
    }

    /** "16 (API 36.1)"; Live Updates need 36.1, which the major version alone doesn't tell. */
    private fun androidVersion(): String {
        val api = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val full = Build.VERSION.SDK_INT_FULL
            "${full / 100_000}.${full % 100_000}"
        } else
            Build.VERSION.SDK_INT.toString()
        return "${Build.VERSION.RELEASE} (API $api)"
    }

    private var approvalRenderer: ((ApprovalPolicy) -> Unit)? = null

    private fun renderApproval(policy: ApprovalPolicy) =
        approvalRenderer?.invoke(policy)

    private companion object {
        const val STEP_UNIT = 10
        const val TEST_CHECK_DELAY_MILLIS = 1_200L
    }
}
