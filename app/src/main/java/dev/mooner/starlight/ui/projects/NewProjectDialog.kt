/*
 * NewProjectDialog.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.getSystemService
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.configdsl.adapters.ConfigAdapter
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.DialogNewProjectBinding
import dev.mooner.starlight.databinding.ItemNewProjectLanguageBinding
import dev.mooner.starlight.databinding.ItemProjectEventBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.GlobalConfig.getDefaultCategory
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.plugincore.project.event.getInstance
import dev.mooner.starlight.ui.common.Sheet
import dev.mooner.starlight.ui.common.showSheet
import dev.mooner.starlight.utils.createFailurePeek
import dev.mooner.starlight.utils.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val LOG = LoggerFactory.logger {  }

private val NAME_REGEX = "^[-_\\dA-Za-zㄱ-ㅎㅏ-ㅣ가-힣]+$".toRegex()

private const val CONFIG_LAST_LANGUAGE = "new_project_language"
private const val DEFAULT_EVENT_ID = "starlight.message.create"
private const val STARLIGHT_PLUGIN_ID = "starlight"
private const val SELECTED_LANGUAGE_COLOR = "#50AD977A"
private const val STEP_ANIM_DURATION = 180L

private enum class Step { NAME, LANGUAGE, EVENTS, CONFIG }

private class EventOption(
    val pluginId: String,
    val id: String,
    val functionName: String,
    val instance: ProjectEvent,
    var isSelected: Boolean,
)

@SuppressLint("CheckResult")
internal fun Fragment.showNewProjectDialog() {
    val context = requireContext()
    val binding = DialogNewProjectBinding.inflate(layoutInflater)
    val languages = Session.languageManager.getLanguages().sortedBy(Language::name)
    val events = loadEventOptions()

    val lastLanguageId = getDefaultCategory().getString(CONFIG_LAST_LANGUAGE)
    var selectedLanguage: Language? = languages.find { it.id == lastLanguageId } ?: languages.firstOrNull()
    var step = Step.NAME

    val configData: MutableDataMap = hashMapOf()
    var configAdapter: ConfigAdapter? = null
    var configLanguage: Language? = null

    fun steps(): List<Step> = buildList {
        add(Step.NAME)
        add(Step.LANGUAGE)
        add(Step.EVENTS)
        if (selectedLanguage?.projectConfigStructure()?.isNotEmpty() == true)
            add(Step.CONFIG)
    }

    lateinit var sheet: Sheet

    val nameInput = binding.newProjectInput
    binding.contentRoot.apply {
        isFocusableInTouchMode = true
        defaultFocusHighlightEnabled = false
    }
    val imm = requireContext().getSystemService<InputMethodManager>()

    fun currentName(): String =
        nameInput.text.toString().trim()

    fun nameError(name: String): String? = when {
        name.isEmpty() -> null
        !NAME_REGEX.matches(name) -> getString(R.string.project_name_format_error)
        projectManager.getProjectByName(name, ignoreCase = true) != null -> getString(R.string.project_duplicate_name)
        else -> null
    }

    fun isNameValid(): Boolean =
        currentName().let { it.isNotEmpty() && nameError(it) == null }

    fun updateNameHelper() {
        val name = currentName()
        val error = nameError(name)
        val language = selectedLanguage
        binding.nameHelper.apply {
            text = when {
                error != null -> error
                name.isEmpty() || language == null -> null
                else -> getString(R.string.project_main_file, "$name.${language.fileExtension}")
            }
            setTextColor(context.getColor(if (error != null) R.color.code_error else R.color.text_sub))
        }
    }

    fun updateButtons() {
        val steps = steps()
        val index = steps.indexOf(step)
        sheet.setPositiveText(getString(if (index == steps.lastIndex) R.string.create else R.string.next))
        sheet.setNegativeText(getString(if (index == 0) R.string.cancel else R.string.previous))
        sheet.setPositiveEnabled(when (step) {
            Step.NAME     -> isNameValid()
            Step.LANGUAGE -> selectedLanguage != null
            else          -> true
        })
    }

    fun updateStepIndicator() {
        val steps = steps()
        val index = steps.indexOf(step)
        binding.stepCounter.text = getString(R.string.new_project_step_counter, index + 1, steps.size)
        binding.stepBar.apply {
            removeAllViews()
            for (i in steps.indices) {
                addView(View(context).apply {
                    setBackgroundResource(R.drawable.bg_step_segment)
                    backgroundTintList = ColorStateList.valueOf(context.getColor(
                        if (i <= index) R.color.main_bright else dev.mooner.configdsl.R.color.configdsl_field
                    ))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                        marginStart = if (i == 0) 0 else dp(4)
                    }
                })
            }
        }
    }

    fun prepareConfig(language: Language) {
        if (configLanguage == language && configAdapter != null)
            return
        configAdapter?.destroy()
        configData.clear()
        configData[language.id] = ConcurrentHashMap()
        configAdapter = ConfigAdapter.Builder(requireContext()) {
            bind(binding.pageConfig)
            structure { language.projectConfigStructure() }
            configData(configData)
        }.build()
        configLanguage = language
    }

    fun showStep(target: Step, animate: Boolean = true, showKeyboard: Boolean = true) {
        if (animate)
            TransitionManager.beginDelayedTransition(binding.contentRoot, AutoTransition().apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                duration = STEP_ANIM_DURATION
            })
        step = target

        val language = selectedLanguage
        val (title, description) = when (target) {
            Step.NAME     -> getString(R.string.new_project_step_name) to getString(R.string.project_name_hint)
            Step.LANGUAGE -> getString(R.string.new_project_step_language) to getString(R.string.new_project_step_language_description)
            Step.EVENTS   -> getString(R.string.new_project_step_events) to getString(R.string.project_events_description)
            Step.CONFIG   -> getString(R.string.new_project_step_config, language?.name) to getString(R.string.new_project_step_config_description)
        }
        binding.stepTitle.text = title
        binding.stepDescription.text = description

        if (target == Step.CONFIG && language != null)
            prepareConfig(language)
        binding.pageName.isVisible = target == Step.NAME
        binding.pageLanguage.isVisible = target == Step.LANGUAGE
        binding.pageEvents.isVisible = target == Step.EVENTS
        binding.pageConfig.isVisible = target == Step.CONFIG

        if (target == Step.NAME) {
            if (showKeyboard) {
                nameInput.requestFocus()
                imm?.showSoftInput(nameInput, InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            imm?.hideSoftInputFromWindow(nameInput.windowToken, 0)
            binding.contentRoot.requestFocus()
        }

        updateStepIndicator()
        updateButtons()
    }

    fun create() {
        val name = currentName()
        val language = selectedLanguage
        if (language == null || !isNameValid()) {
            showStep(Step.NAME)
            return
        }
        val presets = configData[language.id]
            ?.takeIf { configLanguage == language && Step.CONFIG in steps() }
            ?.toMap()
            .orEmpty()
        val selectedEvents = events
            .filter(EventOption::isSelected)
            .associate { it.id to it.instance }
        sheet.dismiss()

        GlobalConfig.edit {
            getDefaultCategory().setString(CONFIG_LAST_LANGUAGE, language.id)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                projectManager.newProject(events = selectedEvents) {
                    this.name  = name
                    mainScript = "$name.${language.fileExtension}"
                    languageId = language.id
                }
                if (presets.isNotEmpty()) {
                    projectManager.getProjectByName(name)?.let { project ->
                        project.config.edit {
                            category(language.id).apply { presets.forEach(::setRaw) }
                        }
                        language.onConfigUpdated(mapOf(language.id to presets))
                    }
                }
            }.onFailure { e ->
                LOG.error { "Failed to create project $name: $e" }
                withContext(Dispatchers.Main) {
                    if (isAdded)
                        createFailurePeek(getString(R.string.project_create_failed, name), PeekAlert.Position.Bottom).peek()
                }
            }
        }
    }

    fun goNext() {
        val steps = steps()
        val index = steps.indexOf(step)
        when {
            step == Step.NAME && !isNameValid() -> updateNameHelper()
            index < steps.lastIndex             -> showStep(steps[index + 1])
            else                                -> create()
        }
    }

    fun goBack(): Boolean {
        val steps = steps()
        val index = steps.indexOf(step)
        if (index <= 0)
            return false
        showStep(steps[index - 1])
        return true
    }

    nameInput.doAfterTextChanged {
        updateNameHelper()
        updateButtons()
    }
    nameInput.setOnEditorActionListener { _, actionId, _ ->
        if (actionId != EditorInfo.IME_ACTION_NEXT && actionId != EditorInfo.IME_ACTION_DONE)
            return@setOnEditorActionListener false
        goNext()
        true
    }

    val languageRows = languages.map { language ->
        ItemNewProjectLanguageBinding.inflate(layoutInflater, binding.pageLanguage, true).apply {
            tvLanguageId.text = "${language.id} · .${language.fileExtension}"
            tvLanguageName.text = language.name
            ivIcon.loadLanguageIcon(language)
        }
    }
    fun updateLanguageSelection() {
        languageRows.forEachIndexed { index, row ->
            row.setLanguageSelected(languages[index] == selectedLanguage)
        }
    }
    languageRows.forEachIndexed { index, row ->
        row.cardWrapper.setOnClickListener {
            selectedLanguage = languages[index]
            updateLanguageSelection()
            updateNameHelper()
            updateStepIndicator()
            updateButtons()
        }
    }
    updateLanguageSelection()

    for (event in events) {
        ItemProjectEventBinding.inflate(layoutInflater, binding.pageEvents, true).apply {
            functionName.text = event.functionName
            eventId.text = event.id
            icon.loadPluginIcon(event.pluginId)
            checkbox.isChecked = event.isSelected
            root.setOnClickListener {
                event.isSelected = !event.isSelected
                checkbox.isChecked = event.isSelected
            }
        }
    }

    sheet = requireActivity().showSheet {
        lifecycleOwner = viewLifecycleOwner
        title = getString(R.string.new_project)
        icon = R.drawable.ic_round_add_24
        content(binding.root)
        cancelOnTouchOutside = false
        positive(R.string.next, autoDismiss = false) { goNext() }
        negative(R.string.cancel, autoDismiss = false) {
            if (!goBack())
                it.dismiss()
        }
        onShow {
            showStep(Step.NAME, animate = false, showKeyboard = false)
        }
        onDismiss {
            configAdapter?.destroy()
            configAdapter = null
        }
    }
    sheet.dialog.setOnKeyListener { _, keyCode, keyEvent ->
        if (keyCode != KeyEvent.KEYCODE_BACK || step == Step.NAME)
            return@setOnKeyListener false
        if (keyEvent.action == KeyEvent.ACTION_UP)
            goBack()
        true
    }

    updateNameHelper()
    updateButtons()
}

private fun Language.projectConfigStructure(): ConfigStructure =
    configStructure.filter { it.id == id }

private fun loadEventOptions(): List<EventOption> =
    ProjectEventManager.filterAllowedEvents(setOf("*"))
        .mapNotNull { id ->
            val instance = ProjectEventManager.findFirst(id)?.getInstance()
                ?: return@mapNotNull null
            EventOption(
                pluginId     = id.substringBefore(ProjectEventManager.PATH_DELIMITER),
                id           = id,
                functionName = instance.functionName,
                instance     = instance,
                isSelected   = id == DEFAULT_EVENT_ID,
            )
        }
        .sortedWith(compareBy({ it.pluginId != STARLIGHT_PLUGIN_ID }, EventOption::pluginId, EventOption::functionName))

private fun ItemNewProjectLanguageBinding.setLanguageSelected(selected: Boolean) {
    val context = root.context
    if (selected) {
        cardWrapper.strokeWidth = dp(2)
        cardWrapper.strokeColor = context.getColor(R.color.main_bright)
        cardWrapper.setCardBackgroundColor(SELECTED_LANGUAGE_COLOR.toColorInt())
    } else {
        cardWrapper.strokeWidth = 0
        cardWrapper.setCardBackgroundColor(context.getColor(dev.mooner.configdsl.R.color.background_selection_card))
    }
}

private fun ImageView.loadLanguageIcon(language: Language) {
    val icon: Any = when {
        language.id == "JS_RHINO" -> R.drawable.ic_js
        else -> language.getIconFileOrNull() ?: R.drawable.ic_round_developer_mode_24
    }
    load(icon) {
        scale(Scale.FIT)
        transformations(RoundedCornersTransformation(resources.getDimension(R.dimen.lang_icon_corner_radius)))
    }
}

private fun ImageView.loadPluginIcon(pluginId: String) {
    if (pluginId == STARLIGHT_PLUGIN_ID) {
        load(R.drawable.ic_logo_transparent)
        return
    }
    val iconFile = Session.pluginManager.getPluginById(pluginId)
        ?.getAsset("icon.png")
        ?.takeIf { it.isFile }
    load(iconFile ?: R.drawable.ic_round_plugins_24) {
        scale(Scale.FIT)
        transformations(RoundedCornersTransformation(resources.getDimension(R.dimen.lang_icon_corner_radius)))
    }
}
