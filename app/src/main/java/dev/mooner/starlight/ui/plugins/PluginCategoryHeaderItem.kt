package dev.mooner.starlight.ui.plugins

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updatePadding
import com.mikepenz.fastadapter.binding.AbstractBindingItem
import dev.mooner.starlight.ID_VIEW_ITEM_PLUGIN_CATEGORY
import dev.mooner.starlight.databinding.ItemPluginCategoryHeaderBinding
import dev.mooner.starlight.plugincore.plugin.PluginCategory

class PluginCategoryHeaderItem(
    private val category: PluginCategory,
    private val name: String,
    private val count: Int,
    private val isFirst: Boolean,
): AbstractBindingItem<ItemPluginCategoryHeaderBinding>() {

    override var identifier: Long
        get() = "category:${category.id}".hashCode().toLong()
        set(_) {}

    override val type: Int
        get() = ID_VIEW_ITEM_PLUGIN_CATEGORY

    override fun bindView(binding: ItemPluginCategoryHeaderBinding, payloads: List<Any>) {
        binding.name.text = name
        binding.count.text = count.toString()
        val top = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, if (isFirst) 6f else 14f, binding.root.resources.displayMetrics)
        binding.root.updatePadding(top = top.toInt())
    }

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemPluginCategoryHeaderBinding =
        ItemPluginCategoryHeaderBinding.inflate(inflater, parent, false)
}
