/*
 * PluginsFragment.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.plugins

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BasicGridItem
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.gridItems
import com.afollestad.materialdialogs.customview.customView
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentPluginsBinding
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.GlobalConfig.getDefaultCategory
import dev.mooner.starlight.plugincore.plugin.StarlightPlugin
import dev.mooner.starlight.utils.align.Align
import dev.mooner.starlight.utils.setCommonAttrs
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class PluginsFragment : Fragment() {

    private var _binding: FragmentPluginsBinding? = null
    private val binding get() = _binding!!

    private var listAdapter: PluginsListAdapter? = null
    private val plugins = pluginManager.plugins

    private val aligns = arrayOf(
        ALIGN_GANADA,
        ALIGN_FILE_SIZE,
    )

    private var alignState: Align<StarlightPlugin> = getAlignByName(
        GlobalConfig
            .getDefaultCategory()
            .getString(CONFIG_PLUGINS_ALIGN, DEFAULT_ALIGN.name)
    )?: DEFAULT_ALIGN

    private var isReversed: Boolean =
        GlobalConfig
            .getDefaultCategory()
            .getString(CONFIG_PLUGINS_ALIGN, DEFAULT_ALIGN.name)
            .toBoolean()

    @SuppressLint("CheckResult")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPluginsBinding.inflate(inflater, container, false)

        if (plugins.isEmpty()) {
            with(binding.textViewNoPluginYet) {
                visibility = View.VISIBLE

                text = if (GlobalConfig.category("plugin").getBoolean("safe_mode", false)) {
                    setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_safe_mode, 0, 0)
                    "플러그인 안전 모드가 켜져있어요."
                } else {
                    setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_box_empty, 0, 0)
                    getString(R.string.no_plugins)
                }
            }
        }

        binding.cardViewPluginAlign.setOnClickListener(::onClick)

        binding.textViewAlignState.text = if (isReversed) alignState.reversedName else alignState.name
        binding.alignStateIcon.setImageResource(alignState.icon)

        listAdapter = PluginsListAdapter(requireContext())

        flowOf(sortData())
            .onEach { sorted ->
                withContext(Dispatchers.Main) {
                    listAdapter?.apply {
                        data = sorted
                        notifyItemRangeInserted(0, plugins.size)
                    }
                }
            }
            .launchIn(lifecycleScope)

        with(binding.recyclerViewProjectList) {
            adapter = listAdapter
            layoutManager = LinearLayoutManager(requireContext())
            itemAnimator = FadeInUpAnimator()
        }

        return binding.root
    }

    private fun onClick(view: View) {
        when(view) {
            binding.cardViewPluginAlign -> showAlignDialog()
        }
    }

    @SuppressLint("CheckResult")
    private fun showAlignDialog() =
        MaterialDialog(requireContext(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            setCommonAttrs()
            gridItems(aligns.toGridItems()) { dialog, _, item ->
                alignState = getAlignByName(item.title)?: DEFAULT_ALIGN
                isReversed = dialog.findViewById<CheckBox>(R.id.checkBoxAlignReversed).isChecked
                update()
            }
            //customView(R.layout.dialog_align_state)
            customView(R.layout.dialog_align_plugins)
            findViewById<CheckBox>(R.id.checkBoxAlignReversed).isChecked = isReversed
        }

    private fun getAlignByName(name: String): Align<StarlightPlugin>? =
        aligns.find { it.name == name }

    private fun Array<Align<StarlightPlugin>>.toGridItems(): List<BasicGridItem> = this.map { item ->
        BasicGridItem(
            iconRes = item.icon,
            title = item.name
        )
    }

    private fun sortData(): List<StarlightPlugin> {
        val aligned = plugins.sortedWith(alignState.comparator)
        return if (isReversed) aligned.asReversed() else aligned
    }

    private fun reloadList(list: List<StarlightPlugin>) {
        listAdapter?.apply {
            val orgDataSize = data.size
            data = listOf()
            notifyItemRangeRemoved(0, orgDataSize)
            data = list
            notifyItemRangeInserted(0, data.size)
        }
    }

    private fun update() {
        binding.textViewAlignState.text = if (isReversed) alignState.reversedName else alignState.name
        binding.alignStateIcon.setImageResource(alignState.icon)
        reloadList(sortData())
        GlobalConfig.edit {
            getDefaultCategory().apply {
                set(CONFIG_PLUGINS_ALIGN, alignState.name)
                set(CONFIG_PLUGINS_REVERSED, isReversed.toString())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listAdapter = null
        _binding = null
    }

    companion object {
        @JvmStatic
        private val ALIGN_GANADA: Align<StarlightPlugin> = Align(
            name = "가나다 순",
            reversedName = "가나다 역순",
            icon = R.drawable.ic_round_sort_by_alpha_24,
            comparator = compareByDescending { it.info.name }
        )

        @JvmStatic
        private val ALIGN_FILE_SIZE: Align<StarlightPlugin> = Align(
            name = "파일 크기 순",
            reversedName = "파일 크기 역순",
            icon = R.drawable.ic_round_plugins_24,
            comparator = compareByDescending { it.fileSize }
        )

        @JvmStatic
        private val DEFAULT_ALIGN = ALIGN_GANADA

        const val CONFIG_PLUGINS_ALIGN = "plugins_align_state"
        const val CONFIG_PLUGINS_REVERSED = "plugins_align_reversed"
    }
}