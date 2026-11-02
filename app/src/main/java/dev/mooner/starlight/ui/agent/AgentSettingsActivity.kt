package dev.mooner.starlight.ui.agent

import android.app.NotificationManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.mooner.starlight.BuildConfig
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.ApprovalPolicy
import dev.mooner.starlight.agent.provider.ProviderKind
import dev.mooner.starlight.agent.store.AgentStorage
import dev.mooner.starlight.agent.store.ConversationArchive
import dev.mooner.starlight.databinding.ActivityAgentSettingsBinding
import dev.mooner.starlight.databinding.ItemAgentOptionBinding
import dev.mooner.starlight.databinding.ItemAgentProviderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Providers to connect, and how the agent behaves. */
class AgentSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgentSettingsBinding
    private val hub by lazy { AgentHub.get(this) }

    private val exportHistory = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null)
            runHistory({ getString(R.string.agent_history_exported, it) }) {
                contentResolver.openOutputStream(uri)!!.use { ConversationArchive.export(hub.dataDir, it) }
            }
    }
    private val importHistory = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null)
            runHistory({ getString(R.string.agent_history_imported, it.imported, it.skipped) }) {
                contentResolver.openInputStream(uri)!!.use { input ->
                    ConversationArchive.import(
                        input = input,
                        staging = File(hub.dataDir, IMPORT_STAGING),
                        attachmentsDir = File(hub.dataDir, AgentStorage.ATTACHMENTS),
                        exists = { id -> hub.store.get(id) != null },
                        add = hub.store::save,
                    )
                }
            }
    }

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
        setUpHistory()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                hub.settings.version.collect {
                    renderProviders()
                    renderLiveUpdates()
                }
            }
        }
    }

    private fun setUpHistory() {
        // Only the shared StarLight folder outlives a reinstall; the app's own folders go with the app.
        val kept = BuildConfig.SHARED_STORAGE && !hub.dataDir.startsWith(filesDir)
        binding.historyBody.setText(if (kept) R.string.agent_history_shared else R.string.agent_history_private)
        binding.exportHistory.setOnClickListener {
            exportHistory.launch("starlight-ai-" + SimpleDateFormat("yyMMdd", Locale.ROOT).format(Date()) + ".zip")
        }
        binding.importHistory.setOnClickListener {
            importHistory.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
    }

    /** Runs [work] off the main thread and says how it went with [done]'s text or the error. */
    private fun <T> runHistory(done: (T) -> String, work: () -> T) {
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { work() } }
                .onSuccess { Toast.makeText(this@AgentSettingsActivity, done(it), Toast.LENGTH_SHORT).show() }
                .onFailure { e ->
                    Toast.makeText(this@AgentSettingsActivity, getString(R.string.agent_history_failed, e.message ?: e.toString()),
                        Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun renderProviders() {
        binding.providers.removeAllViews()
        val active = hub.settings.activeProvider()
        for (kind in ProviderKind.entries) {
            val row = ItemAgentProviderBinding.inflate(LayoutInflater.from(this), binding.providers, true)
            val configured = hub.settings.isConfigured(kind)
            styleProvider(row, kind, configured)
            row.name.text = kind.displayName
            row.method.setText(ProviderBrand.method(kind))
            // A sign-in is what sets ChatGPT apart from an OpenAI key, so it gets the filled chip.
            if (!kind.usesApiKey)
                row.method.setBackgroundResource(R.drawable.bg_agent_chip)
            row.description.setText(ProviderBrand.description(kind))
            row.statusRow.isVisible = configured
            row.status.text = when {
                !configured -> null
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

    /**
     * The provider's logo, and its color around the row: full strength once it's connected, faded until then. A
     * connected one also gets a line saying so, led by a green dot.
     */
    private fun styleProvider(row: ItemAgentProviderBinding, kind: ProviderKind, configured: Boolean) {
        val brand = ProviderBrand.color(this, kind)
        val density = resources.displayMetrics.density
        row.logo.setImageResource(ProviderBrand.logo(kind))
        row.logo.background = ProviderBrand.badge(this, kind)
        val card = GradientDrawable().apply {
            cornerRadius = 20 * density
            setColor(ContextCompat.getColor(this@AgentSettingsActivity, R.color.agent_surface))
            setStroke(((if (configured) 1.5f else 1f) * density).roundToInt(),
                if (configured) brand else ColorUtils.setAlphaComponent(brand, 0x70))
        }
        row.root.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(brand, 0x29)), card, null)

        val dot = if (configured) GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(this@AgentSettingsActivity, R.color.agent_ok))
            val size = (7 * density).roundToInt()
            setSize(size, size)
            setBounds(0, 0, size, size)
        } else null
        row.status.setCompoundDrawablesRelative(dot, null, null, null)
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

        binding.webSearch.isChecked = hub.settings.webSearch
        binding.webSearchRow.setOnClickListener {
            binding.webSearch.isChecked = !binding.webSearch.isChecked
            hub.settings.webSearch = binding.webSearch.isChecked
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
        /** Where an imported zip is unpacked before its conversations are added. */
        const val IMPORT_STAGING = ".import"
        const val STEP_UNIT = 10
    }
}
