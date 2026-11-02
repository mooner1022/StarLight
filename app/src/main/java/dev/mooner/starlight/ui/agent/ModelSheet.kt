package dev.mooner.starlight.ui.agent

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.databinding.ItemAgentModelBinding
import dev.mooner.starlight.databinding.SheetAgentModelBinding
import dev.mooner.starlight.ui.common.showSheet
import kotlinx.coroutines.launch

/** Picks the provider, model and reasoning effort the agent uses next. */
internal object ModelSheet {

    fun <T> show(host: T) where T : Context, T : LifecycleOwner {
        val hub = AgentHub.get(host)
        val providers = hub.settings.configuredProviders()
        if (providers.isEmpty()) {
            host.startActivity(android.content.Intent(host, AgentSettingsActivity::class.java))
            return
        }
        val binding = SheetAgentModelBinding.inflate(LayoutInflater.from(host))
        var kind = hub.settings.activeProvider() ?: providers.first()

        fun renderModels() {
            val selected = hub.settings.model(kind)
            val models = hub.settings.cachedModels(kind).let { list ->
                if (selected != null && list.none { it.id == selected }) list + dev.mooner.starlight.agent.provider.ModelInfo(selected) else list
            }
            binding.models.removeAllViews()
            for (model in models) {
                val row = ItemAgentModelBinding.inflate(LayoutInflater.from(host), binding.models, true)
                row.name.text = model.name
                row.id.text = model.id
                row.id.isVisible = model.name != model.id
                row.check.isVisible = model.id == selected
                row.root.setOnClickListener {
                    hub.settings.setModel(kind, model.id)
                    renderModels()
                }
            }
            binding.customModel.setText(if (models.isEmpty()) selected.orEmpty() else "")
        }

        fun renderEfforts() {
            val levels = kind.effortLevels
            binding.effortLabel.isVisible = levels.isNotEmpty()
            binding.effortScroll.isVisible = levels.isNotEmpty()
            binding.efforts.removeAllViews()
            val current = hub.settings.effort(kind)
            for (level in listOf<String?>(null) + levels) {
                binding.efforts.addView(chip(host, effortLabel(host, level), level == current) {
                    hub.settings.setEffort(kind, level)
                })
            }
        }

        fun refresh() {
            binding.modelStatus.isVisible = true
            binding.modelStatus.text = host.getString(R.string.agent_checking)
            val target = kind
            host.lifecycleScope.launch {
                runCatching { hub.client(target).listModels() }
                    .onSuccess { models ->
                        if (models.isNotEmpty())
                            hub.settings.setCachedModels(target, models)
                        binding.modelStatus.text = host.getString(R.string.agent_check_ok, models.size)
                        if (kind == target)
                            renderModels()
                    }
                    .onFailure { binding.modelStatus.text = host.getString(R.string.agent_check_failed, hub.runtime.describe(it)) }
            }
        }

        for (provider in providers)
            binding.providers.addView(chip(host, provider.displayName, provider == kind) {
                kind = provider
                hub.settings.provider = provider
                binding.modelStatus.isVisible = false
                renderModels()
                renderEfforts()
                if (hub.settings.cachedModels(provider).isEmpty())
                    refresh()
            })
        binding.refresh.setOnClickListener { refresh() }
        binding.applyModel.setOnClickListener {
            val id = binding.customModel.text.toString().trim()
            if (id.isNotEmpty()) {
                hub.settings.setModel(kind, id)
                renderModels()
            }
        }
        renderModels()
        renderEfforts()
        if (hub.settings.cachedModels(kind).isEmpty())
            refresh()

        host.showSheet {
            title = host.getString(R.string.agent_model)
            icon = R.drawable.ic_agent_sparkle
            lifecycleOwner = host
            content(binding.root)
            positive(R.string.agent_done)
        }
    }

    fun effortLabel(context: Context, level: String?): String = context.getString(when (level) {
        "low"    -> R.string.agent_effort_low
        "medium" -> R.string.agent_effort_medium
        "high"   -> R.string.agent_effort_high
        "xhigh"  -> R.string.agent_effort_xhigh
        "max"    -> R.string.agent_effort_max
        else     -> R.string.agent_effort_default
    })

    fun chip(context: Context, text: String, checked: Boolean, onChecked: () -> Unit): Chip =
        Chip(context).apply {
            this.text = text
            isCheckable = true
            isChecked = checked
            isCheckedIconVisible = false
            chipBackgroundColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ContextCompat.getColor(context, R.color.agent_gold), ContextCompat.getColor(context, R.color.agent_surface_alt)),
            )
            setTextColor(ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ContextCompat.getColor(context, R.color.agent_on_gold), ContextCompat.getColor(context, R.color.agent_text)),
            ))
            chipStrokeWidth = 0f
            setOnCheckedChangeListener { _, isChecked -> if (isChecked) onChecked() }
        }
}
