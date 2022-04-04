/*
 * ProjectsFragment.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.gridItems
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.MainActivity
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentProjectsBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.globalConfig
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.utils.align.Align
import dev.mooner.starlight.utils.align.toGridItems
import dev.mooner.starlight.utils.dpToPx
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class ProjectsFragment : Fragment(), View.OnClickListener {

    private var _binding: FragmentProjectsBinding? = null
    private val binding get() = _binding!!

    private var isPaused = false
    private var requiresUpdate = false
    private val updatedProjects: MutableSet<Project> = hashSetOf()

    private lateinit var projects: List<Project>
    private var recyclerAdapter: ProjectListAdapter? = null
    private val aligns = arrayOf(
        ALIGN_GANADA,
        ALIGN_DATE,
        ALIGN_COMPILED
    )
    private var alignState: Align<Project> = getAlignByName(
        globalConfig.getDefaultCategory().getString(CONFIG_PROJECTS_ALIGN, DEFAULT_ALIGN.name)
    )?: DEFAULT_ALIGN
    private var isReversed: Boolean = globalConfig.getDefaultCategory().getString(CONFIG_PROJECTS_REVERSED).toBoolean()
    private var isActiveFirst: Boolean = globalConfig.getDefaultCategory().getString(CONFIG_PROJECTS_ACTIVE_FIRST).toBoolean()

    @SuppressLint("CheckResult")
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectsBinding.inflate(inflater, container, false)

        val activity = activity as MainActivity
        projects = projectManager.getProjects()

        binding.apply {
            fabNewProject.setOnClickListener(this@ProjectsFragment)

            cardViewProjectAlign.setOnClickListener(this@ProjectsFragment)

            textViewAlignState.text = if (isReversed) alignState.reversedName else alignState.name

            alignStateIcon.load(alignState.icon)
        }

        recyclerAdapter = ProjectListAdapter(activity)

        binding.recyclerViewProjectList.apply {
            itemAnimator = FadeInUpAnimator()
            layoutManager = LinearLayoutManager(activity)
            adapter = recyclerAdapter
        }

        lifecycleScope.launchWhenCreated {
            flowOf(sortData())
                .flowOn(Dispatchers.Default)
                .collect { sortedData ->
                    if (projects.isEmpty()) {
                        with(binding.textViewNoProjectYet) {
                            visibility = View.VISIBLE

                            setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_box_empty, 0, 0)
                            text = getString(R.string.nothing_yet)
                                .format("프로젝트가", "(⊙_⊙;)")
                        }
                    }
                    recyclerAdapter!!.apply {
                        data = sortedData
                        notifyItemRangeInserted(0, data.size)
                    }
                    updateTitle(projects)
                }

            Session.eventManager.apply {
                on(this@launchWhenCreated, callback = ::onProjectUpdated)
                on(this@launchWhenCreated, callback = ::onProjectCompiled)
                on(this@launchWhenCreated, callback = ::onProjectDeleted)
                on(this@launchWhenCreated, callback = ::onProjectCreated)
            }
        }

        return binding.root
    }

    override fun onClick(view: View) {
        when(view.id) {
            R.id.cardViewProjectAlign -> showProjectAlignDialog()
            R.id.fabNewProject -> showNewProjectDialog()
        }
    }

    private suspend fun onProjectUpdated(event: Events.Project.ProjectInfoUpdateEvent) = updateProjectView(event.project)

    private suspend fun onProjectCompiled(event: Events.Project.ProjectCompileEvent) = updateProjectView(event.project)

    private suspend fun onProjectDeleted(event: Events.Project.ProjectDeleteEvent) = updateList(null)

    private suspend fun onProjectCreated(event: Events.Project.ProjectCreateEvent) = updateList(event.project)

    private suspend fun updateProjectView(project: Project) {
        if (isPaused)
            updatedProjects += project
        else {
            val index = recyclerAdapter!!.data.indexOf(project)
            if (index == -1) {
                Logger.w(T, "Failed to update project list: index not found")
                return
            }
            withContext(Dispatchers.Main) {
                recyclerAdapter!!.notifyItemChanged(index)
                updateTitle(projects)
            }
        }
    }

    private suspend fun updateList(project: Project?) {
        projects = projectManager.getProjects()
        if (isPaused) {
            requiresUpdate = true
            return
        }
        withContext(Dispatchers.Main) {
            update()
            project?.let { scrollTo(it) }
        }
    }

    private fun updateTitle(projects: List<Project>) {
        val activity = activity as MainActivity

        val count = projects.count { it.info.isEnabled }
        activity.binding.statusText.text = getString(R.string.subtitle_projects).format(count)
    }

    @SuppressLint("CheckResult")
    private fun showProjectAlignDialog() =
        MaterialDialog(requireActivity(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            cornerRadius(res = R.dimen.card_radius)
            maxWidth(res = R.dimen.dialog_width)
            lifecycleOwner(this@ProjectsFragment)
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

    @SuppressLint("CheckResult")
    private fun showNewProjectDialog() =
        MaterialDialog(requireActivity(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            cornerRadius(res = R.dimen.card_radius)
            maxWidth(res = R.dimen.dialog_width)
            customView(R.layout.dialog_new_project)
            lifecycleOwner(this@ProjectsFragment)
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
                    chipMinHeight = dpToPx(context, 30f)
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
        binding.textViewAlignState.text = if (isReversed) alignState.reversedName else alignState.name
        binding.alignStateIcon.load(drawableResId = alignState.icon)
        reloadList(sortData())

        globalConfig.apply {
            set(CONFIG_PROJECTS_ALIGN, alignState.name)
            set(CONFIG_PROJECTS_REVERSED, isReversed.toString())
            set(CONFIG_PROJECTS_ACTIVE_FIRST, isActiveFirst.toString())
            push()
        }
    }

    private fun scrollTo(project: Project) {
        val index = recyclerAdapter!!.data.indexOf(project)
        if (index == -1) {
            Logger.v("Failed to scroll to project: index not found")
            return
        }
        Logger.v("scrollTo= $index")
        binding.recyclerViewProjectList.postDelayed({
            binding.recyclerViewProjectList.smoothScrollToPosition(index)
        }, 500)
    }

    override fun onPause() {
        super.onPause()
        isPaused = true
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        if (requiresUpdate) {
            update()
            requiresUpdate = false
        }
        if (updatedProjects.isNotEmpty()) {
            for (project in updatedProjects) {
                val index = recyclerAdapter!!.data.indexOf(project)
                if (index == -1) {
                    Logger.w(T, "Failed to update project list: index not found")
                    continue
                }
                recyclerAdapter!!.notifyItemChanged(index)
            }
            updatedProjects.clear()
        }
        updateTitle(projects)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerAdapter = null
    }

    companion object {
        private const val T = "ProjectsFragment"

        @JvmStatic
        private val ALIGN_GANADA: Align<Project> = Align(
            name = "가나다 순",
            reversedName = "가나다 역순",
            icon = R.drawable.ic_round_sort_by_alpha_24,
            sort = { list, args ->
                val comparable = with(compareBy<Project> { it.info.isPinned }) {
                    if (args["activeFirst"] == true) {
                        thenBy { !it.info.isEnabled }.thenBy { it.info.name }
                    } else {
                        compareBy { it.info.name }
                    }
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
                val comparable = with(compareBy<Project> { it.info.isPinned }) {
                    if (args["activeFirst"] == true) {
                        thenBy { it.info.isEnabled }.thenBy { it.info.createdMillis }
                    } else {
                        compareBy { it.info.createdMillis }
                    }
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
                val comparable = with(compareBy<Project> { it.info.isPinned }) {
                    if (args["activeFirst"] == true) {
                        thenBy { it.info.isEnabled }.thenBy { it.isCompiled }
                    } else {
                        compareBy { it.isCompiled }
                    }
                }
                list.sortedWith(comparable).asReversed()
            }
        )

        @JvmStatic
        private val DEFAULT_ALIGN = ALIGN_GANADA

        private const val CONFIG_PROJECTS_ALIGN = "projects_align_state"
        private const val CONFIG_PROJECTS_REVERSED = "projects_align_reversed"
        private const val CONFIG_PROJECTS_ACTIVE_FIRST = "projects_align_active_first"
    }
}