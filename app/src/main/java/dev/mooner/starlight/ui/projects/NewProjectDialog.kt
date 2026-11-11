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
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.core.content.getSystemService
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.configdsl.adapters.ConfigAdapter
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.api.ApiLevels
import dev.mooner.starlight.databinding.DialogNewProjectBinding
import dev.mooner.starlight.databinding.ItemApiLevelHeaderBinding
import dev.mooner.starlight.databinding.ItemApiLevelOptionBinding
import dev.mooner.starlight.databinding.ItemNewProjectLanguageBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.api.ApiLevel
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.GlobalConfig.getDefaultCategory
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.event.AllowedEventEntries
import dev.mooner.starlight.ui.common.Sheet
import dev.mooner.starlight.ui.common.setOptionSelected
import dev.mooner.starlight.ui.common.showSheet
import dev.mooner.starlight.ui.projects.events.EventCatalog
import dev.mooner.starlight.ui.projects.events.EventItem
import dev.mooner.starlight.ui.projects.events.EventPickerList
import dev.mooner.starlight.ui.projects.events.EventSource
import dev.mooner.starlight.utils.createFailurePeek
import dev.mooner.starlight.utils.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonElement

private val LOG = LoggerFactory.logger {  }

private val NAME_REGEX = "^[-_\\dA-Za-zㄱ-ㅎㅏ-ㅣ가-힣]+$".toRegex()

private const val CONFIG_LAST_LANGUAGE = "new_project_language"
// StarLight's onMessage and API2's message listener; each shows only when the project has its level.
private val DEFAULT_EVENT_IDS = setOf("starlight.message.create", "starlight.api2.message")
private const val SELECTED_LANGUAGE_COLOR = "#50AD977A"
private const val STEP_ANIM_DURATION = 180L

private enum class Step { NAME, API, LANGUAGE, EVENTS, CONFIG }

/**
 * What the user had filled in when the wizard was taken down together with its screen, as on a rotation or a switch
 * to dark mode. A sheet can't outlive its screen, so the screen that comes in its place shows the wizard again from
 * this, at the step it was on. Held by the activity, which is what lasts through such a change; it doesn't last
 * through the end of the process.
 */
internal class NewProjectDraft : ViewModel() {

    var isOpen = false
        private set
    var name = ""
        private set
    var stepName: String? = null
        private set
    var levelIds: Set<String> = emptySet()
        private set
    var languageId: String? = null
        private set
    var isLanguagePicked = false
        private set
    var eventEntries: AllowedEventEntries? = null
        private set
    var presets: Map<String, Map<String, JsonElement>> = emptyMap()
        private set

    fun keep(
        name: String, stepName: String, levelIds: Set<String>, languageId: String?, isLanguagePicked: Boolean,
        eventEntries: AllowedEventEntries, presets: Map<String, Map<String, JsonElement>>,
    ) {
        isOpen = true
        this.name = name
        this.stepName = stepName
        this.levelIds = levelIds
        this.languageId = languageId
        this.isLanguagePicked = isLanguagePicked
        this.eventEntries = eventEntries
        this.presets = presets
    }

    fun clear() {
        isOpen = false
        name = ""
        stepName = null
        levelIds = emptySet()
        languageId = null
        isLanguagePicked = false
        eventEntries = null
        presets = emptyMap()
    }

    companion object {
        fun of(fragment: Fragment): NewProjectDraft =
            ViewModelProvider(fragment.requireActivity())[NewProjectDraft::class.java]
    }
}

/** [onImport] adds a link on the first step that closes the dialog and imports a project zip instead. */
@SuppressLint("CheckResult")
internal fun Fragment.showNewProjectDialog(onImport: (() -> Unit)? = null) {
    val context = requireContext()
    val binding = DialogNewProjectBinding.inflate(layoutInflater)
    val languages = Session.languageManager.getLanguages().sortedBy(Language::name)
    val eventSources = EventCatalog.load()
    // The wizard as it was when its screen was taken down under it, if it was: it goes on from there.
    val draft = NewProjectDraft.of(this)
    val isRestored = draft.isOpen
    // Kept while the user goes back and forth, as the levels change which events show.
    val eventEntries = draft.eventEntries ?: AllowedEventEntries(DEFAULT_EVENT_IDS)

    val lastLanguageId = getDefaultCategory().getString(CONFIG_LAST_LANGUAGE)
    val initialLanguage: Language? = languages.find { it.id == lastLanguageId } ?: languages.firstOrNull()
    var selectedLanguage: Language? = languages.find { isRestored && it.id == draft.languageId } ?: initialLanguage
    // Once the user picks a language, the levels no longer choose one for them.
    var isLanguagePicked = isRestored && draft.isLanguagePicked
    var step = Step.NAME

    val levels = Session.apiLevelManager.getLevels()
    val selectedLevels: MutableSet<String> = linkedSetOf<String>().apply {
        addAll(if (isRestored) draft.levelIds else Session.apiLevelManager.defaultLevelIds)
    }
    val restoredStep = draft.stepName?.let { name -> Step.entries.find { it.name == name } }
    val restoredName = draft.name
    // The settings of the language's page, put back the first time that page is made.
    var restoredPresets = draft.presets
    draft.clear()

    fun isEventAvailable(item: EventItem): Boolean =
        EventCatalog.isAvailable(selectedLevels, item)

    // Set once the pages' views exist, further down.
    var onLanguageStep: () -> Unit = {}
    var onEventsStep: () -> Unit = {}

    val configData: MutableDataMap = hashMapOf()
    var configAdapter: ConfigAdapter? = null
    var configLanguage: Language? = null

    fun steps(): List<Step> = buildList {
        add(Step.NAME)
        if (levels.isNotEmpty())
            add(Step.API)
        add(Step.LANGUAGE)
        if (eventSources.any { it.events.any(::isEventAvailable) })
            add(Step.EVENTS)
        if (selectedLanguage?.projectConfigStructure()?.isNotEmpty() == true)
            add(Step.CONFIG)
    }

    lateinit var sheet: Sheet
    var backCallback: OnBackPressedCallback? = null

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
            Step.API      -> selectedLevels.isNotEmpty()
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
        configData[language.id] = ConcurrentHashMap(restoredPresets[language.id].orEmpty())
        restoredPresets = emptyMap()
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
        backCallback?.isEnabled = target != Step.NAME

        val language = selectedLanguage
        val (title, description) = when (target) {
            Step.NAME     -> getString(R.string.new_project_step_name) to getString(R.string.project_name_hint)
            Step.API      -> getString(R.string.new_project_step_api) to getString(R.string.new_project_step_api_description)
            Step.LANGUAGE -> getString(R.string.new_project_step_language) to getString(R.string.new_project_step_language_description)
            Step.EVENTS   -> getString(R.string.new_project_step_events) to getString(R.string.project_events_description)
            Step.CONFIG   -> getString(R.string.new_project_step_config, language?.name) to getString(R.string.new_project_step_config_description)
        }
        binding.stepTitle.text = title
        binding.stepDescription.text = description

        if (target == Step.CONFIG && language != null)
            prepareConfig(language)
        if (target == Step.LANGUAGE)
            onLanguageStep()
        if (target == Step.EVENTS)
            onEventsStep()
        binding.pageName.isVisible = target == Step.NAME
        binding.pageApi.isVisible = target == Step.API
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
        val selectedEvents = eventSources
            .flatMap(EventSource::events)
            .filter { eventEntries.isAllowed(it.id) && isEventAvailable(it) }
            .associate { it.id to it.event }
        val apiLevels = selectedLevels.toSet()
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
                    this.apiLevels = apiLevels
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
    val clashWarning = ItemApiLevelHeaderBinding.inflate(layoutInflater, binding.pageLanguage, true).root.apply {
        setText(R.string.api_levels_clash_warning)
        setTextColor(context.getColor(R.color.code_error))
        typeface = null
    }
    fun updateClashWarning() {
        clashWarning.isVisible = ApiLevels.hasListenerNameClash(selectedLevels, selectedLanguage?.id)
    }
    languageRows.forEachIndexed { index, row ->
        row.cardWrapper.setOnClickListener {
            selectedLanguage = languages[index]
            isLanguagePicked = true
            updateLanguageSelection()
            updateClashWarning()
            updateNameHelper()
            updateStepIndicator()
            updateButtons()
        }
    }
    updateLanguageSelection()
    onLanguageStep = {
        // A level may suit a language better, such as API2 and NeonJS, until the user picks one.
        if (!isLanguagePicked) {
            val preferred = levels
                .filter { it.id in selectedLevels }
                .firstNotNullOfOrNull { level -> languages.find { it.id == level.preferredLanguageId } }
            selectedLanguage = preferred ?: initialLanguage
            updateLanguageSelection()
            updateNameHelper()
        }
        updateClashWarning()
    }

    // Grouped under their headings, such as StarLight's own and MessengerBot compatibility.
    val levelCards = levels.groupBy(ApiLevel::group).flatMap { (group, groupLevels) ->
        ItemApiLevelHeaderBinding.inflate(layoutInflater, binding.pageApi, true).root.text = group
        groupLevels.map { level ->
            ItemApiLevelOptionBinding.inflate(layoutInflater, binding.pageApi, true).apply {
                label.text = level.name
                description.text = level.description
                description.isVisible = level.description != null
                objects.text = level.apis.sorted().joinToString(" · ")
                objects.isVisible = level.apis.isNotEmpty()
            }.root to level
        }
    }
    fun updateLevelSelection() =
        levelCards.forEach { (card, level) -> card.setOptionSelected(level.id in selectedLevels) }
    for ((card, level) in levelCards) {
        card.setOnClickListener {
            if (!selectedLevels.remove(level.id))
                selectedLevels += level.id
            updateLevelSelection()
            updateStepIndicator()
            updateButtons()
        }
    }
    updateLevelSelection()

    onEventsStep = {
        // The levels decide which events there are to choose; a choice made earlier stays when it comes back.
        EventPickerList(binding.pageEvents, eventSources, eventEntries, selectedLevels.toSet(), EventPickerList.Mode.SIMPLE) {}
            .build()
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
            showStep(restoredStep?.takeIf { it in steps() } ?: Step.NAME, animate = false, showKeyboard = false)
        }
        onDismiss {
            configAdapter?.destroy()
            configAdapter = null
        }
    }
    // Android takes the screen down to make it anew, and the sheet with it: what was filled in is kept, to be shown
    // again. Seen here and not when the sheet is dismissed, which the sheet tells of only after the screen is gone.
    // Added after the sheet's own observer, so it comes first at the end, while the sheet is still up.
    viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY && sheet.dialog.isShowing && activity?.isChangingConfigurations == true)
            draft.keep(
                name = nameInput.text.toString(),
                stepName = step.name,
                levelIds = selectedLevels.toSet(),
                languageId = selectedLanguage?.id,
                isLanguagePicked = isLanguagePicked,
                eventEntries = eventEntries,
                presets = configData.mapValues { (_, values) -> values.toMap() },
            )
    })
    // Back goes to the previous step. KEYCODE_BACK doesn't arrive with predictive back (targetSdk 36), so
    // this goes through the dispatcher. It is enabled past the first step only, after the sheet registered
    // its own back callback, so it comes first; on the first step the sheet closes as usual.
    backCallback = sheet.dialog.onBackPressedDispatcher.addCallback(enabled = false) { goBack() }

    binding.importFromFile.isVisible = onImport != null
    binding.importFromFile.setOnClickListener {
        sheet.dismiss()
        onImport?.invoke()
    }

    if (isRestored)
        nameInput.setText(restoredName)
    updateNameHelper()
    updateButtons()
}

private fun Language.projectConfigStructure(): ConfigStructure =
    configStructure.filter { it.id == id }

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
