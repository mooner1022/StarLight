/*
 * HomeFragment.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */


package dev.mooner.starlight.ui.home

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.transition.TransitionManager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.button
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentHomeBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.infoTranslated
import dev.mooner.starlight.plugincore.widget.WidgetController
import dev.mooner.starlight.plugincore.widget.WidgetDefinition
import dev.mooner.starlight.plugincore.widget.WidgetInfo
import dev.mooner.starlight.plugincore.widget.WidgetLayout
import dev.mooner.starlight.plugincore.widget.WidgetPlacement
import dev.mooner.starlight.plugincore.widget.WidgetSpan
import dev.mooner.starlight.utils.LAYOUT_TABLET
import dev.mooner.starlight.utils.configStruct
import dev.mooner.starlight.utils.getColorCompat
import dev.mooner.starlight.utils.isDevMode
import dev.mooner.starlight.utils.layoutMode
import dev.mooner.starlight.utils.setCommonAttrsWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import java.util.UUID

class HomeFragment : Fragment(), WidgetGridLayout.Listener {

    private val logger = LoggerFactory.logger {  }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val widgets: MutableMap<View, HomeWidget> = hashMapOf()
    private val hiddenPlacements: MutableList<WidgetPlacement> = arrayListOf()
    private var isLayoutDirty = false

    private val exitEditCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() =
            exitEditMode()
    }

    private class HomeWidget(
        val instanceId: String,
        val definition: WidgetDefinition,
        val card: View,
        val container: FrameLayout,
        var controller: WidgetController?,
        var settings: Map<String, JsonElement>,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        binding.widgetGrid.apply {
            columns = if (requireContext().layoutMode == LAYOUT_TABLET) TABLET_COLUMNS else DEFAULT_COLUMNS
            cellHeight = resources.getDimensionPixelSize(R.dimen.widget_cell_height)
            gap = resources.getDimensionPixelSize(R.dimen.widget_spacing)
            listener = this@HomeFragment
        }
        binding.apply {
            buttonEditAdd.setOnClickListener { showAddWidgetSheet() }
            buttonEditRemove.setOnClickListener { widgetGrid.selected?.let(::removeWidget) }
            buttonEditSettings.setOnClickListener { widgetGrid.selected?.let(::showWidgetSettings) }
            buttonEditDone.setOnClickListener { exitEditMode() }
            buttonEmptyAdd.setOnClickListener {
                enterEditMode()
                showAddWidgetSheet()
            }
            textEditHint.setOnClickListener { enterEditMode() }
            widgetContent.setOnClickListener {
                if (widgetGrid.isEditing)
                    exitEditMode()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, exitEditCallback)

        loadWidgets()

        return binding.root
    }

    private fun loadWidgets() {
        for (placement in HomeLayoutStore.load().items) {
            val definition = Session.widgetManager.getDefinition(placement.widgetId)
            if (definition == null || (definition.isDevOnly && !isDevMode)) {
                if (definition == null)
                    logger.infoTranslated {
                        Locale.ENGLISH { "Skipping unknown widget: ${placement.widgetId}" }
                        Locale.KOREAN  { "알 수 없는 위젯 ${placement.widgetId} 의 초기화 건너뜀" }
                    }
                hiddenPlacements += placement
                continue
            }
            addWidget(placement.instanceId, definition, placement.span, placement.settings)
        }
        updateEmptyState()
    }

    private fun addWidget(
        instanceId: String,
        definition: WidgetDefinition,
        span: WidgetSpan?,
        settings: Map<String, JsonElement> = emptyMap(),
    ): View {
        val grid = binding.widgetGrid
        val card = layoutInflater.inflate(R.layout.card_widget_grid, grid, false)
        val container = card.findViewById<FrameLayout>(R.id.view)
        val widget = HomeWidget(instanceId, definition, card, container, definition.newController(), settings)
        widgets[card] = widget
        grid.addWidget(card, definition.spanSpec, span, definition.isAutoHeight)

        widget.controller!!.apply {
            onError = { e -> card.post { onWidgetError(widget, e) } }
            create(container, definition.spanSpec.resolve(span, grid.columns), isPreview = false, settings = settings)
            attach(viewLifecycleOwner.lifecycle)
        }
        return card
    }

    private fun removeWidget(card: View) {
        val widget = widgets.remove(card) ?: return
        widget.controller?.destroy()
        binding.widgetGrid.removeWidget(card)
        updateEmptyState()
        saveLayout()
    }

    private fun onWidgetError(widget: HomeWidget, throwable: Throwable) {
        if (_binding == null || widgets[widget.card] !== widget)
            return
        val controller = widget.controller ?: return
        logger.error(throwable) {
            translate {
                Locale.ENGLISH { "Widget '${widget.definition.id}' failed: " }
                Locale.KOREAN  { "위젯 '${widget.definition.id}' 실행 중 오류 발생: " }
            }
        }
        widget.controller = null
        controller.destroy()
        widget.container.removeAllViews()
        layoutInflater.inflate(R.layout.widget_error, widget.container, true)
    }

    private fun saveLayout() {
        val grid = binding.widgetGrid
        val items = grid.widgets.mapNotNull { card ->
            widgets[card]?.let { widget ->
                WidgetPlacement(widget.instanceId, widget.definition.id, grid.spanOf(card), widget.settings)
            }
        }
        HomeLayoutStore.save(WidgetLayout(items = items + hiddenPlacements))
        isLayoutDirty = false
    }

    private fun updateEmptyState() {
        val isEmpty = widgets.isEmpty()
        binding.emptyState.isVisible = isEmpty
        binding.textEditHint.isVisible = !isEmpty && !binding.widgetGrid.isEditing
    }

    private fun enterEditMode() {
        binding.widgetGrid.setEditing(true)
        onEditModeChanged()
    }

    private fun exitEditMode() {
        binding.widgetGrid.setEditing(false)
        onEditModeChanged()
        if (isLayoutDirty)
            saveLayout()
    }

    private fun onEditModeChanged() {
        val isEditing = binding.widgetGrid.isEditing
        exitEditCallback.isEnabled = isEditing
        TransitionManager.beginDelayedTransition(binding.root as ViewGroup)
        binding.editBar.isVisible = isEditing
        updateEmptyState()
    }

    private fun showAddWidgetSheet() {
        MaterialDialog(requireContext(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            setCommonAttrsWithLifecycle()
            maxWidth(res = R.dimen.dialog_width)
            title(res = R.string.home_add_widget)
            configStruct(requireContext()) {
                struct(getAddWidgetStructure(::dismiss))
            }
        }
    }

    private fun showWidgetSettings(card: View) {
        val widget = widgets[card] ?: return
        val form = widget.definition.createSettingsForm(widget.settings) ?: return
        MaterialDialog(requireContext(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            setCommonAttrsWithLifecycle()
            maxWidth(res = R.dimen.dialog_width)
            title(text = widget.definition.name)
            configStruct(requireContext()) {
                struct(form.structure)
                data { form.data }
                onUpdate { _, id, _, jsonValue ->
                    val value = form.toValue(id, jsonValue) ?: return@onUpdate
                    withContext(Dispatchers.Main) { applySetting(widget, id, value) }
                }
            }
        }
    }

    private fun applySetting(widget: HomeWidget, id: String, value: JsonElement) {
        if (_binding == null || widgets[widget.card] !== widget)
            return
        widget.settings = widget.settings + (id to value)
        widget.controller?.updateSettings(widget.settings)
        saveLayout()
    }

    private fun getAddWidgetStructure(onDismiss: () -> Unit): ConfigStructure = config {
        val manager = Session.widgetManager
        val available = manager.getAllWidgetInfo().values.filter { info ->
            manager.getDefinition(info.id)?.let { !it.isDevOnly || isDevMode } == true
        }
        for ((category, infos) in available.groupBy(WidgetInfo::pluginName)) {
            category {
                id = UUID.randomUUID().toString()
                title = category
                textColor = requireContext().getColorCompat(R.color.main_bright)
                items {
                    for (info in infos) {
                        val definition = manager.getDefinition(info.id) ?: continue
                        button {
                            id = info.id
                            title = definition.name
                            setOnClickListener { _ ->
                                onWidgetPicked(definition)
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onWidgetPicked(definition: WidgetDefinition) {
        if (_binding == null)
            return
        val card = addWidget(WidgetLayout.newInstanceId(), definition, span = null)
        binding.widgetGrid.select(card)
        updateEmptyState()
        saveLayout()
        card.post {
            card.requestRectangleOnScreen(Rect(0, 0, card.width, card.height), false)
        }
    }

    override fun onEditModeRequested(view: View) =
        onEditModeChanged()

    override fun onSelectionChanged(view: View?) {
        binding.buttonEditRemove.isVisible = view != null
        binding.buttonEditSettings.isVisible = view?.let(widgets::get)?.definition?.hasSettings == true
    }

    override fun onSpanChanged(view: View, span: WidgetSpan) {
        val widget = widgets[view] ?: return
        widget.controller?.resize(widget.definition.spanSpec.resolve(span, binding.widgetGrid.columns))
        isLayoutDirty = true
    }

    override fun onOrderChanged() {
        isLayoutDirty = true
    }

    override fun onEmptyAreaTapped() =
        exitEditMode()

    override fun onPause() {
        if (binding.widgetGrid.isEditing)
            exitEditMode()
        super.onPause()
    }

    override fun onDestroyView() {
        if (isLayoutDirty)
            saveLayout()
        widgets.values.forEach { it.controller?.destroy() }
        widgets.clear()
        hiddenPlacements.clear()
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DEFAULT_COLUMNS = 4
        const val TABLET_COLUMNS = 8
    }
}
