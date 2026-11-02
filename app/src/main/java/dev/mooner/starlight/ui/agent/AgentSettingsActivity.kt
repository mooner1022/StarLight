package dev.mooner.starlight.ui.agent

import android.app.NotificationManager
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

        binding.instructions.setText(hub.settings.customInstructions)
        binding.instructions.doAfterTextChanged { hub.settings.customInstructions = it?.toString().orEmpty() }
    }

    override fun onResume() {
        super.onResume()
        // The user may come back from the system's setting for it.
        renderLiveUpdates()
    }

    /** Says so when the system setting keeps the app from showing Live Updates, with a way there. */
    private fun renderLiveUpdates() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA)
            return
        val blocked = getSystemService(NotificationManager::class.java)?.canPostPromotedNotifications() == false
        binding.liveUpdatesStatus.isVisible = hub.settings.liveUpdates && blocked
        binding.liveUpdatesStatus.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }.onFailure {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
    }

    private var approvalRenderer: ((ApprovalPolicy) -> Unit)? = null

    private fun renderApproval(policy: ApprovalPolicy) =
        approvalRenderer?.invoke(policy)

    private companion object {
        const val STEP_UNIT = 10
    }
}
