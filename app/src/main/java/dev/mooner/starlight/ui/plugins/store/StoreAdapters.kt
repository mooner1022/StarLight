package dev.mooner.starlight.ui.plugins.store

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import coil.dispose
import coil.load
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ItemStoreFeaturedBinding
import dev.mooner.starlight.databinding.ItemStorePluginBinding
import dev.mooner.starlight.databinding.ItemStoreScreenshotBinding
import dev.mooner.starlight.plugincore.plugin.PluginDependency
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun Context.formatSize(bytes: Long): String =
    Formatter.formatShortFileSize(this, bytes)

internal fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

internal fun compactDate(millis: Long): String =
    if (System.currentTimeMillis() - millis < DateUtils.WEEK_IN_MILLIS) relativeTime(millis)
    else SimpleDateFormat("yy.MM.dd", Locale.getDefault()).format(Date(millis))

internal fun StoreIndex.categoryName(id: String): String =
    categories.find { it.id == id }?.name?.get() ?: id

internal fun ImageView.loadStoreImage(path: String?, @DrawableRes placeholder: Int = R.drawable.ic_store_icon_placeholder) {
    if (path == null) {
        dispose()
        setImageResource(placeholder)
        return
    }
    load(StoreSource.resolve(path)) {
        placeholder(placeholder)
        error(placeholder)
        crossfade(true)
    }
}

internal fun Context.dependencyRange(dependency: PluginDependency): String =
    if (dependency.supportedVersion == PluginDependency.VERSION_ANY)
        getString(R.string.plugin_store_dep_any)
    else
        getString(R.string.plugin_store_dep_min, dependency.supportedVersion)

internal fun StorePlugin.authorNames(): String =
    authors.joinToString { it.name }

private fun View.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

class StorePluginAdapter(
    private val onClick: (StorePlugin, View) -> Unit,
    private val onInstall: (StorePlugin) -> Unit,
    private val onCompleted: (View) -> Unit,
) : ListAdapter<StorePlugin, StorePluginAdapter.Holder>(DIFF) {

    class Holder(val binding: ItemStorePluginBinding) : RecyclerView.ViewHolder(binding.root)

    var installStates: Map<String, StoreInstallState> = emptyMap()
        set(value) {
            val old = field
            field = value
            currentList.forEachIndexed { position, plugin ->
                if (old[plugin.id] != value[plugin.id])
                    notifyItemChanged(position, PAYLOAD_STATE)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemStorePluginBinding.inflate(LayoutInflater.from(parent.context), parent, false)).apply {
            binding.install.compact = true
            binding.iconFrame.clipToOutline = true
            binding.install.onCompleted = onCompleted
        }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (PAYLOAD_STATE in payloads)
            holder.bindState(getItem(position), animate = true)
        else
            super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val plugin = getItem(position)
        val context = holder.itemView.context
        with(holder.binding) {
            card.transitionName = "store_row_${plugin.id}"
            icon.loadStoreImage(plugin.icon)
            name.text = plugin.name
            verified.visibility = if (plugin.isVerified) View.VISIBLE else View.GONE
            summary.text = plugin.summary
            meta.text = listOf(
                plugin.authors.first().name,
                "v${plugin.latest.version}",
                context.formatSize(plugin.latest.size),
            ).joinToString(" · ")
            card.setOnClickListener { onClick(plugin, card) }
            install.setOnClickListener { onInstall(plugin) }
        }
        holder.bindState(plugin, animate = false)
    }

    private fun Holder.bindState(plugin: StorePlugin, animate: Boolean) {
        val state = installStates[plugin.id] ?: return
        binding.install.setState(state, animate)
    }

    companion object {
        private const val PAYLOAD_STATE = "state"

        private val DIFF = object : DiffUtil.ItemCallback<StorePlugin>() {
            override fun areItemsTheSame(oldItem: StorePlugin, newItem: StorePlugin): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: StorePlugin, newItem: StorePlugin): Boolean =
                oldItem == newItem
        }
    }
}

class StoreFeaturedAdapter(
    private val onClick: (StorePlugin, View) -> Unit,
) : RecyclerView.Adapter<StoreFeaturedAdapter.Holder>() {

    class Holder(val binding: ItemStoreFeaturedBinding) : RecyclerView.ViewHolder(binding.root)

    private var items: List<Pair<StoreFeature, StorePlugin>> = emptyList()
    private var index: StoreIndex? = null

    fun submit(index: StoreIndex) {
        if (this.index === index)
            return
        this.index = index
        items = index.featured.mapNotNull { feature -> index.find(feature.pluginId)?.let { feature to it } }
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int =
        items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemStoreFeaturedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val fadeDistance = binding.root.dp(72).toFloat()
        binding.iconFrame.clipToOutline = true
        binding.mask.setOnMaskChangedListener { mask ->
            binding.text.translationX = mask.left
            binding.text.alpha = 1f - (mask.left / fadeDistance).coerceIn(0f, 1f)
            binding.bigIcon.translationX = -(binding.root.width - mask.right) * 0.6f
        }
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val (feature, plugin) = items[position]
        with(holder.binding) {
            mask.transitionName = "store_featured_${plugin.id}"
            stars.seed = position * 31 + 5
            icon.loadStoreImage(plugin.icon)
            category.text = index?.categoryName(plugin.category)
            headline.text = feature.headline.get()
            caption.text = "${plugin.name} · ${plugin.authorNames()}"
            mask.setOnClickListener { onClick(plugin, mask) }
        }
    }
}

class StoreScreenshotAdapter(
    private val plugin: StorePlugin,
    private val onClick: (View) -> Unit,
) : RecyclerView.Adapter<StoreScreenshotAdapter.Holder>() {

    class Holder(val binding: ItemStoreScreenshotBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int =
        plugin.screenshots.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemStoreScreenshotBinding.inflate(LayoutInflater.from(parent.context), parent, false)).apply {
            binding.frame.clipToOutline = true
        }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val screenshot = plugin.screenshots[position]
        with(holder.binding) {
            image.loadStoreImage(screenshot.image, R.drawable.ic_store_screenshot_placeholder)
            caption.text = screenshot.caption
            caption.visibility = if (screenshot.caption.isEmpty()) View.GONE else View.VISIBLE
            frame.setOnClickListener(onClick)
        }
    }
}
