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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.gridItems
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import dev.mooner.starlight.MainActivity
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentProjectsBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.GlobalConfig.getDefaultCategory
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.utils.align.Align
import dev.mooner.starlight.utils.align.toGridItems
import dev.mooner.starlight.utils.setCommonAttrs
import dev.mooner.starlight.utils.warn
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform

private val LOG = LoggerFactory.logger {  }

class ProjectsFragment : Fragment(), View.OnClickListener {

    private var _binding: FragmentProjectsBinding? = null
    private val binding get() = _binding!!

    private var updateReceiveScope: CoroutineScope? = null

    private val updatedProjects: MutableSet<Project> = hashSetOf()

    private lateinit var projects: List<Project>
    private var itemAdapter: ItemAdapter<ProjectListItem>? = null

    private val aligns = arrayOf(
        ALIGN_GANADA,
        ALIGN_DATE,
        ALIGN_COMPILED
    )
    private var alignState: Align<Project> = getAlignByName(
        getDefaultCategory().getString(CONFIG_PROJECTS_ALIGN, DEFAULT_ALIGN.name)
    )?: DEFAULT_ALIGN
    private var isReversed: Boolean =
        getDefaultCategory().getString(CONFIG_PROJECTS_REVERSED).toBoolean()
    private var isActiveFirst: Boolean =
        getDefaultCategory().getString(CONFIG_PROJECTS_ACTIVE_FIRST).toBoolean()

    @SuppressLint("CheckResult")
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectsBinding.inflate(inflater, container, false)

        //val activity = activity as MainActivity
        //recyclerAdapter = ProjectListAdapter(activity)
        itemAdapter = ItemAdapter()
        val fastAdapter = FastAdapter.with(itemAdapter!!)

        projects = projectManager.getProjects()

        binding.apply {
            fabNewProject.setOnClickListener(this@ProjectsFragment)

            cardViewProjectAlign.setOnClickListener(this@ProjectsFragment)

            textViewAlignState.text = if (isReversed) alignState.reversedName else alignState.name

            alignStateIcon.load(alignState.icon)

            recyclerViewProjectList.setup(fastAdapter)
        }

        //updateTitle(projects)

        updateReceiveScope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { scope ->
            EventHandler.apply {
                on(scope, callback = ::onProjectUpdated)
                on(scope, callback = ::onProjectCompiled)
                on(scope, callback = ::onProjectDeleted)
                on(scope, callback = ::onProjectCreated)
            }
        }

        lifecycleScope.launchWhenCreated {
            updateEmptyText()
            launch {
                sortDataAsync()
                    .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
                    .transform { list ->
                        emit(list.map { ProjectListItem().withProject(it) })
                    }
                    .collect(itemAdapter!!::set)
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

    private suspend fun onProjectUpdated(event: Events.Project.InfoUpdate) =
        itemAdapter?.updateProjectView(event.project)

    private suspend fun onProjectCompiled(event: Events.Project.Compile) =
        itemAdapter?.updateProjectView(event.project)

    private suspend fun onProjectDeleted(unused: Events.Project.Delete) =
        updateList(null)

    private suspend fun onProjectCreated(event: Events.Project.Create) =
        updateList(event.project)

    private fun RecyclerView.setup(adapter: FastAdapter<ProjectListItem>) {
        itemAnimator = FadeInUpAnimator()
        layoutManager = LinearLayoutManager(activity)
        this.adapter = adapter
    }

    private suspend fun updateEmptyText() = withContext(Dispatchers.Main) {
        if (projects.isEmpty()) {
            with(binding.textViewNoProjectYet) {
                visibility = View.VISIBLE

                setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_box_empty, 0, 0)
                text = getString(R.string.no_projects)
            }
        } else {
            binding.textViewNoProjectYet.visibility = View.GONE
        }
    }

    private suspend fun ItemAdapter<ProjectListItem>.updateProjectView(project: Project) {
        val index = getAdapterPosition(project.info.id.hashCode().toLong())
        LOG.debug { "updateProjectView ${project.info.name}, index $index" }
        if (index == -1) {
            with(requireContext()) {
                LOG.warn(R.string.log_project_list_update_failure)
            }
            return
        }
        withContext(Dispatchers.Main) {
            //this@updateProjectView.getAdapterItem(index)
            //!!.notifyItemChanged(index)
            getAdapterItem(index).tryUpdateView()
            updateTitle(projects)
        }
    }

    private suspend fun updateList(project: Project?) {
        projects = projectManager.getProjects()
        withContext(Dispatchers.Main) {
            updateEmptyText()
            update()

            project?.let { itemAdapter?.scrollTo(it) }
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
            setCommonAttrs()
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
            setCommonAttrs()
            customView(R.layout.dialog_new_project, scrollable = false, horizontalPadding = true)
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
                Chip(this.windowContext).apply {
                    id = index
                    text = language.name
                    setChipBackgroundColorResource(R.color.chip_selector)
                    isCheckable = true
                    if (index == 0) {
                        isChecked = true
                    }
                }.also(chipGroup::addView)
            }

            positiveButton(res = R.string.create) {
                val projectName = nameEditText.text.toString()
                if (projectManager.getProject(projectName, ignoreCase = true) != null) {
                    nameEditText.error = getString(R.string.project_duplicate_name)
                    nameEditText.requestFocus()
                    return@positiveButton
                }
                if (!"(^[-_\\dA-Za-zㄱ-ㅎㅏ-ㅣ가-힣]+\$)".toRegex().matches(projectName)) {
                    nameEditText.error = getString(R.string.project_name_format_error)
                    nameEditText.requestFocus()
                    return@positiveButton
                }

                val id = chipGroup.checkedChipId
                if (id == View.NO_ID) {
                    Snackbar.make(this.view, getString(R.string.project_select_language), Snackbar.LENGTH_SHORT).show()
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
            negativeButton(res = R.string.cancel) {
                it.dismiss()
            }
            //onDismiss {
            //    binding.fabNewProject.show()
            //`}
        }

    private fun getAlignByName(name: String): Align<Project>? =
        aligns.find { it.name == name }

    private fun sortDataAsync(): Flow<List<Project>> =
        flow {
            val comparable = compareByDescending<Project> { it.info.isPinned }
                .thenByDescending { it.isCompiled }
                .thenComparing(alignState.comparator)
            emit(projects
                .sortedWith(comparable)
                .let { if (isReversed) it.reversed() else it })
        }

    private fun reloadList(list: List<ProjectListItem>) {
        itemAdapter?.set(list)
        /*
        recyclerAdapter?.apply {
            val orgDataSize = data.size
            data = listOf()
            notifyItemRangeRemoved(0, orgDataSize)
            data = list
            notifyItemRangeInserted(0, list.size)
        }
         */
    }

    private fun update() {

        lifecycleScope.launch {
            sortDataAsync()
                .transform { list ->
                    emit(list.map { ProjectListItem().withProject(it) })
                }
                .collect { list ->
                    LOG.verbose { list.size }
                    reloadList(list)
                }
        }

        binding.textViewAlignState.text = if (isReversed) alignState.reversedName else alignState.name
        binding.alignStateIcon.load(alignState.icon)

        GlobalConfig.edit {
            getDefaultCategory().apply {
                set(CONFIG_PROJECTS_ALIGN, alignState.name)
                set(CONFIG_PROJECTS_REVERSED, isReversed.toString())
                set(CONFIG_PROJECTS_ACTIVE_FIRST, isActiveFirst.toString())
            }
        }
    }
    private fun ItemAdapter<ProjectListItem>.scrollTo(project: Project) {
        val index = getAdapterPosition(project.hashCode().toLong())
        if (index == -1) {
            with(requireContext()) {
                LOG.warn(R.string.log_project_list_update_failure)
            }
            return
        }
        binding.recyclerViewProjectList.postDelayed({
            binding.recyclerViewProjectList.smoothScrollToPosition(index)
        }, 500)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (updateReceiveScope != null) {
            updateReceiveScope!!.cancel()
            updateReceiveScope = null
        }
        if (itemAdapter != null)
            itemAdapter = null
    }

    override fun onResume() {
        super.onResume()
        //isPaused = false
        if (updatedProjects.isNotEmpty()) {
            for (project in updatedProjects) {
                val index = itemAdapter?.getAdapterPosition(project.hashCode().toLong())
                if (index == -1) {
                    with(requireContext()) {
                        LOG.warn(R.string.log_project_list_update_failure)
                    }
                    continue
                }
                //recyclerAdapter!!.notifyItemChanged(index)
            }
            updatedProjects.clear()
        }
        updateTitle(projects)
    }

    companion object {
        @JvmStatic
        private val ALIGN_GANADA: Align<Project> = Align(
            name = "가나다 순",
            reversedName = "가나다 역순",
            icon = R.drawable.ic_round_sort_by_alpha_24,
            comparator = compareBy { it.info.name }
        )

        @JvmStatic
        private val ALIGN_DATE: Align<Project> = Align(
            name = "생성일 순",
            reversedName = "생성일 역순",
            icon = R.drawable.ic_baseline_edit_calendar_24,
            comparator = compareByDescending { it.info.createdMillis }
        )

        @JvmStatic
        private val ALIGN_COMPILED: Align<Project> = Align(
            name = "컴파일 순",
            reversedName = "미 컴파일 순",
            icon = R.drawable.ic_round_refresh_24,
            comparator = compareByDescending { it.isCompiled }
        )

        @JvmStatic
        private val DEFAULT_ALIGN = ALIGN_GANADA

        private const val CONFIG_PROJECTS_ALIGN = "projects_align_state"
        private const val CONFIG_PROJECTS_REVERSED = "projects_align_reversed"
        private const val CONFIG_PROJECTS_ACTIVE_FIRST = "projects_align_active_first"
    }
}