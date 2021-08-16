package com.mooner.starlight.ui.plugins

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BasicGridItem
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.gridItems
import com.afollestad.materialdialogs.customview.customView
import com.mooner.starlight.R
import com.mooner.starlight.databinding.FragmentPluginsBinding
import com.mooner.starlight.models.Align
import com.mooner.starlight.plugincore.core.GeneralConfig
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.plugin.Plugin
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import com.mooner.starlight.utils.Utils
import jp.wasabeef.recyclerview.animators.FadeInLeftAnimator

class PluginsFragment : Fragment() {

    private var _binding: FragmentPluginsBinding? = null
    private val binding get() = _binding!!

    private lateinit var listAdapter: PluginsListAdapter
    private lateinit var plugins: List<Plugin>

    private val aligns = arrayOf(
        ALIGN_GANADA,
        ALIGN_FILE_SIZE,
    )
    private var alignState: Align<Plugin> = getAlignByName(
        Session.getGeneralConfig()[GeneralConfig.CONFIG_PLUGINS_ALIGN, DEFAULT_ALIGN.name]
    )?: DEFAULT_ALIGN
    private var isReversed: Boolean = Session.getGeneralConfig()[GeneralConfig.CONFIG_PLUGINS_REVERSED, "false"].toBoolean()

    companion object {
        private val ALIGN_GANADA = Align<Plugin>(
            name = "가나다 순",
            reversedName = "가나다 역순",
            icon = R.drawable.ic_round_sort_by_alpha_24,
            sort = { list, _ ->
                list.sortedBy { it.name }
            }
        )

        private val ALIGN_FILE_SIZE = Align<Plugin>(
            name = "파일 크기 순",
            reversedName = "파일 크기 역순",
            icon = R.drawable.ic_round_plugins_24,
            sort = { list, _ ->
                list.sortedByDescending { (it as StarlightPlugin).fileSize }
            }
        )

        private val DEFAULT_ALIGN = ALIGN_GANADA
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPluginsBinding.inflate(inflater, container, false)
        plugins = Session.pluginLoader.getPlugins().toList()

        if (plugins.isEmpty()) {
            Logger.i(javaClass.simpleName, "No plugins detected!")
            with(binding.textViewNoPluginYet) {
                visibility = View.VISIBLE
                text = Utils.formatStringRes(
                    R.string.nothing_yet,
                    mapOf(
                        "name" to "플러그인이",
                        "emoji" to "(>_<｡)\uD83D\uDCA6"
                    )
                )
            }
        }

        binding.textViewPluginAlignState.text = Utils.formatStringRes(
            R.string.plugin_align_state,
            mapOf(
                "state" to if (isReversed) alignState.reversedName else alignState.name
            )
        )
        binding.imageViewPluginAlignState.setImageResource(alignState.icon)

        binding.cardViewPluginAlign.setOnClickListener {
            MaterialDialog(requireContext(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                cornerRadius(25f)
                gridItems(getGridItems(*aligns)) { dialog, _, item ->
                    alignState = getAlignByName(item.title)?: DEFAULT_ALIGN
                    isReversed = dialog.findViewById<CheckBox>(R.id.checkBoxAlignReversed).isChecked
                    update()
                }
                //customView(R.layout.dialog_align_state)
                customView(R.layout.dialog_align_plugins)
                findViewById<CheckBox>(R.id.checkBoxAlignReversed).isChecked = isReversed
            }
        }

        listAdapter = PluginsListAdapter(requireContext())
        println("plugins: $plugins")
        listAdapter.data = plugins
        listAdapter.notifyItemRangeInserted(0, plugins.size)
        with(binding.recyclerViewProjectList) {
            this.adapter = listAdapter
            this.layoutManager = LinearLayoutManager(requireContext())
            this.itemAnimator = FadeInLeftAnimator()
        }

        return binding.root
    }

    private fun getAlignByName(name: String): Align<Plugin>? {
        return aligns.find { it.name == name }
    }

    private fun getGridItems(vararg items: Align<Plugin>): List<BasicGridItem> {
        val list: MutableList<BasicGridItem> = mutableListOf()
        for (item in items) {
            list.add(
                BasicGridItem(
                    item.icon,
                    item.name
                )
            )
        }
        return list
    }

    private fun reloadList(list: List<Plugin>) {
        with(listAdapter) {
            val orgDataSize = data.size
            data = listOf()
            notifyItemRangeRemoved(0, orgDataSize)
            data = list
            notifyItemRangeInserted(0, data.size)
        }
    }

    private fun update(align: Align<Plugin> = alignState, isReversed: Boolean = this.isReversed) {
        binding.textViewPluginAlignState.text = Utils.formatStringRes(
            R.string.plugin_align_state,
            mapOf(
                "state" to if (isReversed) align.reversedName else align.name
            )
        )
        binding.imageViewPluginAlignState.setImageResource(align.icon)
        reloadList(
            if (isReversed) {
                align.sort(
                    plugins,
                    mapOf()
                ).asReversed()
            } else {
                align.sort(
                    plugins,
                    mapOf()
                )
            }
        )
        Session.getGeneralConfig().also {
            it[GeneralConfig.CONFIG_PLUGINS_ALIGN] = align.name
            it[GeneralConfig.CONFIG_PLUGINS_REVERSED] = isReversed.toString()
            it.push()
            println("push!")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}