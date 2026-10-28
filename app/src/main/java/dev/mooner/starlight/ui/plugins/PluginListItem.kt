package dev.mooner.starlight.ui.plugins

import android.content.Intent
import android.content.res.ColorStateList
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import coil.load
import coil.size.Scale
import com.mikepenz.fastadapter.binding.AbstractBindingItem
import dev.mooner.starlight.ID_VIEW_ITEM_PLUGIN
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ItemPluginActionsBinding
import dev.mooner.starlight.databinding.ItemPluginBinding
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.plugin.InstalledPlugin
import dev.mooner.starlight.plugincore.plugin.PluginChange
import dev.mooner.starlight.plugincore.plugin.PluginInfo
import dev.mooner.starlight.plugincore.plugin.PluginState
import dev.mooner.starlight.ui.plugins.config.PluginConfigActivity
import dev.mooner.starlight.ui.plugins.info.startPluginInfoActivity

class PluginListItem(
    private val key: String,
    private val callback: Callback,
): AbstractBindingItem<ItemPluginBinding>() {

    interface Callback {
        fun onToggle(item: PluginListItem, info: PluginInfo, enabled: Boolean)
        fun onRemove(item: PluginListItem, key: String)
    }

    override var identifier: Long
        get() = key.hashCode().toLong()
        set(_) {}

    override val type: Int
        get() = ID_VIEW_ITEM_PLUGIN

    var entry: InstalledPlugin? = null
        private set
    var change: PluginChange? = null
        private set

    val info: PluginInfo?
        get() = change?.info ?: entry?.info

    val name: String
        get() = info?.takeUnless { entry?.isDuplicate == true }?.name ?: entry?.file?.name ?: key

    private var binding: ItemPluginBinding? = null
    private var isExpanded: Boolean = false

    fun with(entry: InstalledPlugin?, change: PluginChange?): PluginListItem {
        this.entry = entry
        this.change = change
        return this
    }

    override fun bindView(binding: ItemPluginBinding, payloads: List<Any>) {
        this.binding = binding
        binding.root.tag = this
        binding.header.setOnClickListener {
            if (!isExpandable())
                return@setOnClickListener
            isExpanded = !isExpanded
            binding.setExpanded(isExpanded, animate = true)
        }
        binding.updateState()
        binding.setExpanded(isExpanded, animate = false)
    }

    override fun unbindView(binding: ItemPluginBinding) {
        if (binding.root.tag === this)
            binding.root.tag = null
        if (this.binding === binding)
            this.binding = null
    }

    fun tryUpdateView() {
        val binding = binding?.takeIf { it.root.tag === this } ?: return
        binding.updateState()
        binding.setExpanded(isExpanded, animate = false)
    }

    private fun isExpandable(): Boolean =
        change !is PluginChange.Removed

    private fun ItemPluginBinding.updateState() {
        val context = root.context
        val info = info
        title.text = name

        val status = context.pluginStatus(entry, change)
        subtitle.text = status.text
        subtitle.setTextColor(context.getColor(if (status.isProblem) status.color else R.color.text_sub))
        this.status.backgroundTintList = ColorStateList.valueOf(context.getColor(status.color))

        val iconFile = info?.id?.let(context::pluginIconFile)?.takeIf { it.isFile }
        if (iconFile != null) {
            icon.imageTintList = null
            icon.load(iconFile) { scale(Scale.FIT) }
        } else {
            icon.imageTintList = ColorStateList.valueOf(context.getColor(R.color.text))
            icon.load(R.drawable.ic_round_plugins_24)
        }

        val canToggle = info != null && change !is PluginChange.Removed &&
                entry?.state !is PluginState.Invalid && entry?.isDuplicate != true
        toggle.setOnCheckedChangeListener(null)
        toggle.visibility = if (canToggle) View.VISIBLE else View.INVISIBLE
        if (canToggle) {
            toggle.isChecked = pluginManager.isPluginEnabled(info!!.id)
            toggle.setOnCheckedChangeListener { _, isChecked ->
                callback.onToggle(this@PluginListItem, info, isChecked)
            }
        }
        arrow.visibility = if (isExpandable()) View.VISIBLE else View.INVISIBLE
        header.alpha = if (change is PluginChange.Removed) 0.5f else 1f
    }

    private fun ItemPluginBinding.findActions(inflate: Boolean): ItemPluginActionsBinding? {
        root.findViewById<View>(R.id.actions)?.let { return ItemPluginActionsBinding.bind(it) }
        if (!inflate)
            return null
        return ItemPluginActionsBinding.bind(actionsStub.inflate())
    }

    private fun ItemPluginBinding.setExpanded(expanded: Boolean, animate: Boolean) {
        val expanded = expanded && isExpandable()
        if (animate)
            (root.parent?.parent as? ViewGroup)?.let {
                TransitionManager.beginDelayedTransition(it, AutoTransition().apply {
                    ordering = TransitionSet.ORDERING_TOGETHER
                    duration = ANIM_DURATION
                })
            }
        val actions = findActions(inflate = expanded)
        if (actions != null) {
            actions.root.visibility = if (expanded) View.VISIBLE else View.GONE
            if (expanded)
                actions.bind()
        }
        val rotation = if (expanded) 180f else 0f
        if (animate)
            arrow.animate().rotation(rotation).setDuration(ANIM_DURATION).start()
        else {
            arrow.animate().cancel()
            arrow.rotation = rotation
        }
    }

    private fun ItemPluginActionsBinding.bind() {
        val entry = entry
        val loaded = (entry?.state as? PluginState.Loaded)?.plugin
        buttonPluginConfig.setActionEnabled(loaded != null && change == null)
        buttonPluginInfo.setActionEnabled(entry != null)
        buttonPluginRemove.setActionEnabled(true)

        buttonPluginConfig.setOnClickListener { view ->
            loaded ?: return@setOnClickListener
            view.context.startActivity(
                Intent(view.context, PluginConfigActivity::class.java)
                    .putExtra(PluginConfigActivity.EXTRA_PLUGIN_NAME, loaded.info.name)
                    .putExtra(PluginConfigActivity.EXTRA_PLUGIN_ID, loaded.info.id)
            )
        }
        buttonPluginInfo.setOnClickListener { view ->
            view.context.startPluginInfoActivity(entry ?: return@setOnClickListener)
        }
        buttonPluginRemove.setOnClickListener {
            callback.onRemove(this@PluginListItem, key)
        }
    }

    private fun TextView.setActionEnabled(enabled: Boolean) {
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.35f
    }

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemPluginBinding =
        ItemPluginBinding.inflate(inflater, parent, false)

    companion object {
        private const val ANIM_DURATION = 200L
    }
}
