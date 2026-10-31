/*
 * PluginsFragment.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.plugins

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.GenericItem
import com.mikepenz.fastadapter.adapters.ItemAdapter
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.MainActivity
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentPluginsBinding
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.GlobalConfig.getDefaultCategory
import dev.mooner.starlight.plugincore.plugin.ApiCompatibility
import dev.mooner.starlight.plugincore.plugin.InstalledPlugin
import dev.mooner.starlight.plugincore.plugin.PluginCategory
import dev.mooner.starlight.plugincore.plugin.PluginInfo
import dev.mooner.starlight.plugincore.plugin.PluginState
import dev.mooner.starlight.ui.common.showSheet
import dev.mooner.starlight.ui.plugins.store.PluginStoreActivity
import dev.mooner.starlight.utils.align.Align
import dev.mooner.starlight.utils.align.toGridItems
import dev.mooner.starlight.utils.createFailurePeek
import dev.mooner.starlight.utils.createSuccessPeek
import dev.mooner.starlight.utils.restartApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PluginsFragment : Fragment(), View.OnClickListener, PluginListItem.Callback {

    private var _binding: FragmentPluginsBinding? = null
    private val binding get() = _binding!!

    private val listItems: MutableMap<String, PluginListItem> = hashMapOf()
    private var itemAdapter: ItemAdapter<GenericItem>? = null
    private var searchQuery: String = ""

    private val aligns = arrayOf(
        ALIGN_GANADA,
        ALIGN_FILE_SIZE,
    )

    private var alignState: Align<PluginListItem> =
        getAlignByName(
            getDefaultCategory()
                .getString(CONFIG_PLUGINS_ALIGN, DEFAULT_ALIGN.name)
        )?: DEFAULT_ALIGN

    private var isReversed: Boolean =
        getDefaultCategory()
            .getString(CONFIG_PLUGINS_REVERSED)
            .toBoolean()

    private val pickPluginFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null)
            onPluginFilePicked(uri)
    }

    @SuppressLint("CheckResult")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPluginsBinding.inflate(inflater, container, false)

        itemAdapter = ItemAdapter()
        val fastAdapter = FastAdapter.with(itemAdapter!!)

        binding.apply {
            updateAlignChip()

            alignPlugin.setOnClickListener(this@PluginsFragment)
            pluginStore.setOnClickListener(this@PluginsFragment)
            searchPlugin.setOnClickListener(this@PluginsFragment)
            searchClose.setOnClickListener(this@PluginsFragment)
            installPlugin.setOnClickListener(this@PluginsFragment)

            searchInput.doAfterTextChanged { text ->
                val query = text?.toString()?.trim().orEmpty()
                if (query != searchQuery) {
                    searchQuery = query
                    submitList()
                }
            }
            searchInput.setOnEditorActionListener { view, actionId, _ ->
                if (actionId != EditorInfo.IME_ACTION_SEARCH)
                    return@setOnEditorActionListener false
                requireContext().getSystemService<InputMethodManager>()
                    ?.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
                true
            }

            recyclerViewPluginList.layoutManager = LinearLayoutManager(requireContext())
            recyclerViewPluginList.adapter = fastAdapter
        }

        submitList()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null)
            submitList()
    }

    override fun onClick(view: View) {
        when(view.id) {
            R.id.align_plugin   -> showAlignDialog()
            R.id.plugin_store   -> startActivity(Intent(requireContext(), PluginStoreActivity::class.java))
            R.id.search_plugin  -> setSearchVisible(binding.searchBar.visibility != View.VISIBLE)
            R.id.searchClose    -> setSearchVisible(false)
            R.id.install_plugin -> pickPluginFile.launch(arrayOf("*/*"))
        }
    }

    override fun onToggle(item: PluginListItem, info: PluginInfo, enabled: Boolean) {
        val dependents = if (enabled) emptyList() else requiredDependentsOf(info.id)
        if (dependents.isEmpty()) {
            setPluginEnabled(item, info, enabled)
            return
        }
        requireContext().showSheet {
            lifecycleOwner = viewLifecycleOwner
            title = getString(R.string.plugin_disable_title, info.name)
            message = getString(R.string.plugin_dependents_warning, dependents.joinToString())
            icon = R.drawable.ic_round_warning_24
            positive(R.string.plugin_disable) {
                setPluginEnabled(item, info, false)
            }
            negative(R.string.cancel)
            onDismiss { item.tryUpdateView() }
        }
    }

    private fun setPluginEnabled(item: PluginListItem, info: PluginInfo, enabled: Boolean) {
        pluginManager.setPluginEnabled(info.id, enabled)
        if (_binding == null)
            return
        item.tryUpdateView()
        updateNotice()
        updateTitle()
    }

    override fun onRemove(item: PluginListItem, key: String) {
        showRemovePluginDialog(
            requireContext(),
            key,
            onRemoved = { name ->
                onPluginsChanged()
                createSuccessPeek(getString(R.string.plugin_remove_done, name), PeekAlert.Position.Bottom).peekAboveBars()
            },
            onFailure = { e ->
                onPluginsChanged()
                createFailurePeek(e.message ?: e.toString(), PeekAlert.Position.Bottom).peekAboveBars()
            }
        )
    }

    private fun onPluginFilePicked(uri: Uri) {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val file = runCatching {
                withContext(Dispatchers.IO) { copyToCache(uri) }
            }.getOrElse { e ->
                createFailurePeek(getString(R.string.plugin_install_failed, e.message ?: e.toString()), PeekAlert.Position.Bottom).peekAboveBars()
                return@launch
            }
            val info = runCatching { context.pluginInstaller().inspect(file) }
                .getOrElse { e ->
                    file.delete()
                    createFailurePeek(getString(R.string.plugin_install_invalid, e.message ?: e.toString()), PeekAlert.Position.Bottom).peekAboveBars()
                    return@launch
                }
            showInstallDialog(file, info)
        }
    }

    private fun copyToCache(uri: Uri): File {
        val resolver = requireContext().contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
            ?: "plugin.slp"
        val dir = requireContext().cacheDir.resolve("plugin_install").apply {
            deleteRecursively()
            mkdirs()
        }
        val file = dir.resolve(name)
        resolver.openInputStream(uri)!!.use { input ->
            file.outputStream().use(input::copyTo)
        }
        return file
    }

    private fun showInstallDialog(file: File, info: PluginInfo) {
        val current = knownPlugins().find { it.id == info.id }
        val message = buildString {
            append(info.name).append(' ').append(info.displayVersion())
            append('\n').append(info.id)
            if (info.authors.isNotEmpty())
                append(" · ").append(info.authors.joinToString())
            if (info.description.isNotBlank())
                append("\n\n").append(info.description)
            append("\n\n")
            append(when {
                current == null ->
                    getString(R.string.plugin_install_new)
                current.version == info.version ->
                    getString(R.string.plugin_install_reinstall, info.displayVersion())
                current.version > info.version ->
                    getString(R.string.plugin_install_downgrade, current.displayVersion(), info.displayVersion())
                else ->
                    getString(R.string.plugin_install_update, current.displayVersion(), info.displayVersion())
            })
            requireContext().apiCompatibilityText(ApiCompatibility.of(info.apiVersion))?.let { append("\n⚠ ").append(it) }
            val known = knownPlugins().map(PluginInfo::id).toSet()
            val missing = info.dependency.filter { !it.isOptional && it.pluginId !in known }
            if (missing.isNotEmpty())
                append("\n⚠ ").append(getString(R.string.plugin_install_missing_dependencies, missing.joinToString { it.pluginId }))
        }

        val context = requireContext()
        var installed = false
        context.showSheet {
            lifecycleOwner = viewLifecycleOwner
            title = getString(R.string.plugin_install_title)
            this.message = message
            icon = R.drawable.ic_round_plugins_24
            positive(R.string.plugin_install) {
                installed = true
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) { pluginManager.install(context.pluginInstaller(), file) }
                    }
                    file.delete()
                    if (_binding == null)
                        return@launch
                    onPluginsChanged()
                    result
                        .onSuccess {
                            createSuccessPeek(getString(R.string.plugin_install_done, info.name), PeekAlert.Position.Bottom).peekAboveBars()
                            scrollTo(info.id)
                        }
                        .onFailure { e ->
                            createFailurePeek(getString(R.string.plugin_install_failed, e.message ?: e.toString()), PeekAlert.Position.Bottom).peekAboveBars()
                        }
                }
            }
            negative(R.string.cancel)
            onDismiss {
                if (!installed)
                    file.delete()
            }
        }
    }

    private fun PeekAlert.peekAboveBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            view?.rootWindowInsets?.let { insets ->
                setMargin(vertical = insets.getInsets(WindowInsets.Type.systemBars()).bottom)
            }
        peek()
    }

    private fun onPluginsChanged() {
        if (_binding == null)
            return
        submitList()
    }

    private fun setSearchVisible(visible: Boolean) {
        val input = binding.searchInput
        val imm = requireContext().getSystemService<InputMethodManager>()
        binding.searchBar.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            input.requestFocus()
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        } else {
            imm?.hideSoftInputFromWindow(input.windowToken, 0)
            input.text.clear()
            input.clearFocus()
        }
    }

    private fun showAlignDialog() =
        requireContext().showSheet {
            lifecycleOwner = viewLifecycleOwner
            title = getString(R.string.align_title)
            icon = R.drawable.ic_round_sort_by_alpha_24
            grid(aligns.toGridItems(), selected = aligns.indexOf(alignState)) { sheet, index ->
                alignState = aligns[index]
                isReversed = sheet.isChecked(0)
                update()
            }
            checkbox(R.string.align_reversed, isReversed)
        }

    private fun getAlignByName(name: String): Align<PluginListItem>? =
        aligns.find { it.name == name }

    private fun collectItems(): List<PluginListItem> {
        val changes = pluginManager.changes
        val entries: Map<String, InstalledPlugin?> = linkedMapOf<String, InstalledPlugin?>().apply {
            for (entry in pluginManager.installed)
                put(entry.key, entry)
            for (key in changes.keys)
                if (key !in this)
                    put(key, null)
        }
        listItems.keys.retainAll(entries.keys)
        return entries.map { (key, entry) ->
            listItems.getOrPut(key) { PluginListItem(key, this) }
                .with(entry, changes[key])
        }
    }

    private fun sortedItems(): List<PluginListItem> {
        val align = alignState.comparator
        val comparator = Comparator<PluginListItem> { a, b -> align.compare(a, b) }
            .let { if (isReversed) it.reversed() else it }

        val query = searchQuery
        return collectItems()
            .filter { item -> query.isEmpty() || item.matches(query) }
            .sortedWith(comparator)
    }

    private fun PluginListItem.matches(query: String): Boolean {
        val info = info
        return name.contains(query, ignoreCase = true) ||
                info?.id?.contains(query, ignoreCase = true) == true ||
                info?.authors?.any { it.contains(query, ignoreCase = true) } == true
    }

    private fun groupByCategory(items: List<PluginListItem>): List<GenericItem> {
        val groups = items.groupBy(PluginListItem::category)
        val order = groups.keys.sortedBy(PluginCategory::ordinal)
        return order.flatMapIndexed { i, category ->
            val members = groups.getValue(category)
            listOf(PluginCategoryHeaderItem(category, requireContext().pluginCategoryName(category), members.size, isFirst = i == 0)) + members
        }
    }

    private fun submitList() {
        val items = sortedItems()
        itemAdapter?.set(groupByCategory(items))
        updateEmptyText(isFiltered = searchQuery.isNotEmpty(), isEmpty = items.isEmpty())
        updateNotice()
        updateTitle()
    }

    private fun updateEmptyText(isFiltered: Boolean, isEmpty: Boolean) {
        with(binding.textViewNoPluginYet) {
            when {
                !isEmpty -> visibility = View.GONE
                isFiltered -> {
                    visibility = View.VISIBLE
                    setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    text = getString(R.string.plugin_search_empty)
                }
                else -> {
                    visibility = View.VISIBLE
                    setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_box_empty, 0, 0)
                    text = getString(R.string.no_plugins)
                }
            }
        }
    }

    private fun updateNotice() {
        val isSafeMode = pluginManager.installed.any { it.state == PluginState.SafeMode } ||
                GlobalConfig.category("plugin").getBoolean("safe_mode", false)
        with(binding) {
            when {
                pluginManager.requiresRestart -> {
                    notice.visibility = View.VISIBLE
                    noticeIcon.setImageResource(R.drawable.ic_round_refresh_24)
                    noticeText.setText(R.string.plugin_notice_restart)
                    noticeAction.setText(R.string.plugin_restart)
                    noticeAction.setOnClickListener { requireContext().restartApplication() }
                }
                isSafeMode -> {
                    notice.visibility = View.VISIBLE
                    noticeIcon.setImageResource(R.drawable.ic_safe_mode)
                    noticeText.setText(R.string.plugin_notice_safe_mode)
                    noticeAction.setText(R.string.plugin_safe_mode_off)
                    noticeAction.setOnClickListener {
                        GlobalConfig.edit {
                            category("plugin")["safe_mode"] = false
                        }
                        requireContext().restartApplication()
                    }
                }
                else ->
                    notice.visibility = View.GONE
            }
        }
    }

    private fun updateTitle() {
        val activity = activity as? MainActivity ?: return
        if (isResumed)
            activity.binding.statusText.text = requireContext().pluginsSubtitle()
    }

    private fun scrollTo(key: String) {
        val position = itemAdapter?.getAdapterPosition(key.hashCode().toLong()) ?: return
        if (position >= 0)
            binding.recyclerViewPluginList.smoothScrollToPosition(position)
    }

    private fun updateAlignChip() {
        binding.alignPlugin.setChipIconResource(alignState.icon)
        binding.alignPlugin.text = getString(R.string.aligned_by)
            .format(if (isReversed) alignState.reversedName else alignState.name)
    }

    private fun update() {
        updateAlignChip()
        submitList()
        GlobalConfig.edit {
            getDefaultCategory().apply {
                set(CONFIG_PLUGINS_ALIGN, alignState.name)
                set(CONFIG_PLUGINS_REVERSED, isReversed.toString())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        itemAdapter = null
        listItems.clear()
        _binding = null
    }

    companion object {
        @JvmStatic
        private val ALIGN_GANADA: Align<PluginListItem> = Align(
            name = "가나다 순",
            reversedName = "가나다 역순",
            icon = R.drawable.ic_round_sort_by_alpha_24,
            comparator = compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )

        @JvmStatic
        private val ALIGN_FILE_SIZE: Align<PluginListItem> = Align(
            name = "파일 크기 순",
            reversedName = "파일 크기 역순",
            icon = R.drawable.ic_round_plugins_24,
            comparator = compareByDescending { it.entry?.file?.length() ?: 0L }
        )

        @JvmStatic
        private val DEFAULT_ALIGN = ALIGN_GANADA

        const val CONFIG_PLUGINS_ALIGN = "plugins_align_state"
        const val CONFIG_PLUGINS_REVERSED = "plugins_align_reversed"
    }
}
