package com.mooner.starlight.ui.projects

import android.animation.LayoutTransition
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.gridItems
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.R
import com.mooner.starlight.databinding.FragmentProjectsBinding
import com.mooner.starlight.models.Align
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Session.globalConfig
import com.mooner.starlight.plugincore.core.Session.projectManager
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.utils.ViewUtils
import com.mooner.starlight.utils.toGridItems
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectsFragment : Fragment() {
    
    companion object {
        private const val T = "ProjectsFragment"

        @JvmStatic
        private val ALIGN_GANADA: Align<Project> = Align(
            name = "가나다 순",
            reversedName = "가나다 역순",
            icon = R.drawable.ic_round_sort_by_alpha_24,
            sort = { list, args ->
                val comparable = if (args["activeFirst"] == true) {
                    compareBy<Project> { !it.info.isEnabled }.thenBy { it.info.name }
                } else {
                    compareBy { it.info.name }
                }
                list.sortedWith(comparable)
            }
        )

        @JvmStatic
        private val ALIGN_DATE: Align<Project> = Align(
            name = "생성일 순",
            reversedName = "생성일 역순",
            icon = R.drawable.ic_baseline_edit_calendar_24,
            sort = { list, args ->
                val comparable = if (args["activeFirst"] == true) {
                    compareBy<Project> { it.info.isEnabled }.thenBy { it.info.createdMillis }
                } else {
                    compareBy { it.info.createdMillis }
                }
                list.sortedWith(comparable).asReversed()
            }
        )

        @JvmStatic
        private val ALIGN_COMPILED: Align<Project> = Align(
            name = "컴파일 순",
            reversedName = "미 컴파일 순",
            icon = R.drawable.ic_round_refresh_24,
            sort = { list, args ->
                val comparable = if (args["activeFirst"] == true) {
                    compareBy<Project> { it.info.isEnabled }.thenBy { it.isCompiled }
                } else {
                    compareBy { it.isCompiled }
                }
                list.sortedWith(comparable).asReversed()
            }
        )

        @JvmStatic
        private val DEFAULT_ALIGN = ALIGN_GANADA

        const val CONFIG_PROJECTS_ALIGN = "projects_align_state"
        const val CONFIG_PROJECTS_REVERSED = "projects_align_reversed"
        const val CONFIG_PROJECTS_ACTIVE_FIRST = "projects_align_active_first"
    }

    private var _binding: FragmentProjectsBinding? = null
    private val binding get() = _binding!!

    private var isPaused = false
    private val updatedProjects: MutableSet<Project> = hashSetOf()

    private lateinit var projects: List<Project>
    private var recyclerAdapter: ProjectListAdapter? = null
    private val aligns = arrayOf(
        ALIGN_GANADA,
        ALIGN_DATE,
        ALIGN_COMPILED
    )
    private var alignState: Align<Project> = getAlignByName(
        globalConfig[CONFIG_PROJECTS_ALIGN, DEFAULT_ALIGN.name]
    )?: DEFAULT_ALIGN
    private var isReversed: Boolean = globalConfig[CONFIG_PROJECTS_REVERSED, "false"].toBoolean()
    private var isActiveFirst: Boolean = globalConfig[CONFIG_PROJECTS_ACTIVE_FIRST, "false"].toBoolean()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectsBinding.inflate(inflater, container, false)

        binding.cardViewProjectAlign.setOnClickListener {
            MaterialDialog(requireContext(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                cornerRadius(25f)
                gridItems(aligns.toGridItems()) { dialog, _, item ->
                    alignState = getAlignByName(item.title)?: DEFAULT_ALIGN
                    isReversed = dialog.findViewById<CheckBox>(R.id.checkBoxAlignReversed).isChecked
                    isActiveFirst = dialog.findViewById<CheckBox>(R.id.checkBoxAlignActiveFirst).isChecked
                    update()
                }
                //customView(R.layout.dialog_align_state)
                customView(R.layout.dialog_align_projects)
                findViewById<CheckBox>(R.id.checkBoxAlignReversed).isChecked = isReversed
                findViewById<CheckBox>(R.id.checkBoxAlignActiveFirst).isChecked = isActiveFirst
            }
        }

        binding.alignState.text = if (isReversed) alignState.reversedName else alignState.name
        binding.alignStateIcon.setImageResource(alignState.icon)

        binding.fabNewProject.setOnClickListener { _ ->
            //binding.fabNewProject.hide()
            MaterialDialog(requireActivity(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                cornerRadius(27f)
                customView(R.layout.dialog_new_project)
                cancelOnTouchOutside(true)
                noAutoDismiss()

                val nameEditText: EditText = findViewById(R.id.editTextNewProjectName)

                //val languageSpinner: NiceSpinner
                // = findViewById(R.id.spinnerLanguage)
                nameEditText.text.clear()

                val chipGroup: ChipGroup = this.findViewById(R.id.langSelectionGroup)
                chipGroup.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
                val languages = Session.languageManager.getLanguages()
                for ((index, language) in languages.withIndex()) {
                    val chip = Chip(this.windowContext).apply {
                        id = index
                        text = language.name
                        chipMinHeight = ViewUtils.dpToPx(requireContext(), 30f)
                        isCheckable = true
                        if (index == 0) {
                            isChecked = true
                        }
                    }
                    chipGroup.addView(chip)
                }

                positiveButton(text = "생성") {
                    val projectName = nameEditText.text.toString()
                    if (projectManager.getProject(projectName) != null) {
                        nameEditText.error = "이미 존재하는 이름이에요."
                        nameEditText.requestFocus()
                        return@positiveButton
                    }
                    if (!"(^[-_0-9A-Za-zㄱ-ㅎㅏ-ㅣ가-힣]+\$)".toRegex().matches(projectName)) {
                        nameEditText.error = "이름은 숫자와 -, _, 영문자, 한글만 가능해요."
                        nameEditText.requestFocus()
                        return@positiveButton
                    }

                    val id = chipGroup.checkedChipId
                    if (id == View.NO_ID) {
                        Snackbar.make(this.view, "사용할 언어를 선택해주세요.", Snackbar.LENGTH_SHORT).show()
                        return@positiveButton
                    }
                    val selectedLang = Session.languageManager.getLanguages()[id]
                    projectManager.newProject {
                        name = projectName
                        mainScript = "$projectName.${selectedLang.fileExtension}"
                        languageId = selectedLang.id
                        createdMillis = System.currentTimeMillis()
                        listeners = hashSetOf("default")
                    }
                    it.dismiss()
                }
                negativeButton(text = "취소") {
                    it.dismiss()
                }
                //onDismiss {
                //    binding.fabNewProject.show()
                //`}
            }
        }

        recyclerAdapter = ProjectListAdapter(requireContext())

        CoroutineScope(Dispatchers.Default).launch {
            projects = projectManager.getProjects()
            val sortedData = sortData()

            withContext(Dispatchers.Main) {
                recyclerAdapter!!.apply {
                    data = sortedData
                    notifyItemRangeInserted(0, data.size)
                }
            }
        }

        with(binding.recyclerViewProjectList) {
            itemAnimator = FadeInUpAnimator()
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recyclerAdapter
        }

        projectManager.addOnStateChangedListener(T) { project ->
            if (isPaused)
                updatedProjects += project
            else {
                val index = recyclerAdapter!!.data.indexOf(project)
                if (index == -1) {
                    Logger.v(T, "Failed to update project list: index not found")
                    return@addOnStateChangedListener
                }
                CoroutineScope(Dispatchers.Main).launch {
                    recyclerAdapter!!.notifyItemChanged(index)
                }
            }
        }

        bindListener()
        return binding.root
    }

    private fun getAlignByName(name: String): Align<Project>? = aligns.find { it.name == name }

    private fun sortData(): List<Project> {
        val aligned = alignState.sort(
            projects,
            mapOf(
                "activeFirst" to isActiveFirst
            )
        )
        return if (isReversed) aligned.asReversed() else aligned
    }

    private fun reloadList(list: List<Project>) {
        recyclerAdapter?.apply {
            val orgDataSize = data.size
            data = listOf()
            notifyItemRangeRemoved(0, orgDataSize)
            data = list
            notifyItemRangeInserted(0, list.size)
        }
    }

    private fun update() {
        binding.alignState.text = if (isReversed) alignState.reversedName else alignState.name
        binding.alignStateIcon.load(drawableResId = alignState.icon)
        reloadList(sortData())

        globalConfig.apply {
            set(CONFIG_PROJECTS_ALIGN, alignState.name)
            set(CONFIG_PROJECTS_REVERSED, isReversed.toString())
            set(CONFIG_PROJECTS_ACTIVE_FIRST, isActiveFirst.toString())
            push()
        }
    }

    override fun onPause() {
        super.onPause()
        isPaused = true
        unbindListener()
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        bindListener()
        if (this.projects.size != projectManager.getProjects().size) {
            this.projects = projectManager.getProjects()
            update()
        }
        if (updatedProjects.isNotEmpty()) {
            for (project in updatedProjects) {
                val index = recyclerAdapter!!.data.indexOf(project)
                if (index == -1) {
                    Logger.v(T, "Failed to update project list: index not found")
                    continue
                }
                recyclerAdapter!!.notifyItemChanged(index)
            }
            updatedProjects.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unbindListener()
        projectManager.removeOnStateChangedListener(T)
        recyclerAdapter = null
    }

    private fun bindListener() {
        projectManager.addOnListUpdatedListener(T) { projects ->
            this.projects = projects
            update()
        }
    }

    private fun unbindListener() {
        projectManager.removeOnListUpdatedListener(T)
    }
}