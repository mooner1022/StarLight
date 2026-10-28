/*
 * ProjectsFragment.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import androidx.core.content.getSystemService
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.gridItems
import com.afollestad.materialdialogs.customview.customView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.MainActivity
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentProjectsBinding
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.GlobalConfig.getDefaultCategory
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectID
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.utils.*
import dev.mooner.starlight.utils.align.Align
import dev.mooner.starlight.utils.align.toGridItems
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import kotlin.math.min

class ProjectsFragment : Fragment(), View.OnClickListener {

    private var _binding: FragmentProjectsBinding? = null
    private val binding get() = _binding!!

    private var updateReceiveScope: CoroutineScope? = null

    private var projects: List<Project> = emptyList()
    private val listItems: MutableMap<ProjectID, ProjectListItem> = hashMapOf()
    private var itemAdapter: ItemAdapter<ProjectListItem>? = null
    private var searchQuery: String = ""

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

        itemAdapter = ItemAdapter()
        val fastAdapter = FastAdapter.with(itemAdapter!!)

        projects = projectManager.getProjects()

        binding.apply {
            updateAlignChip()

            alignProject.setOnClickListener(this@ProjectsFragment)
            searchProject.setOnClickListener(this@ProjectsFragment)
            searchClose.setOnClickListener(this@ProjectsFragment)
            newProject.setOnClickListener(this@ProjectsFragment)

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

            recyclerViewProjectList.setup(fastAdapter)
        }

        updateReceiveScope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { scope ->
            EventHandler.apply {
                on(scope, callback = ::onProjectUpdated)
                on(scope, callback = ::onProjectCompiled)
                on(scope, callback = ::onProjectDeleted)
                on(scope, callback = ::onProjectCreated)
            }
        }

        submitList()

        return binding.root
    }

    override fun onClick(view: View) {
        when(view.id) {
            R.id.align_project -> showProjectAlignDialog()
            R.id.search_project -> setSearchVisible(binding.searchBar.visibility != View.VISIBLE)
            R.id.searchClose -> setSearchVisible(false)
            R.id.new_project -> showNewProjectDialog()
        }
    }

    private suspend fun onProjectUpdated(event: Events.Project.InfoUpdate) =
        updateProjectView(event.project)

    private suspend fun onProjectCompiled(event: Events.Project.Compile) =
        updateProjectView(event.project)

    private suspend fun onProjectDeleted(event: Events.Project.Delete) =
        notifyProjectRemoved(event.projectId, event.projectName)

    private suspend fun onProjectCreated(event: Events.Project.Create) =
        notifyProjectAdded(event.project)

    private fun RecyclerView.setup(adapter: FastAdapter<ProjectListItem>) {
        //itemAnimator = FadeInUpAnimator()
        layoutManager = LinearLayoutManager(activity)
        this.adapter = adapter
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

    private fun updateEmptyText(isFiltered: Boolean, isEmpty: Boolean) {
        with(binding.textViewNoProjectYet) {
            when {
                !isEmpty -> visibility = View.GONE
                isFiltered -> {
                    visibility = View.VISIBLE
                    setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    text = getString(R.string.project_search_empty)
                }
                else -> {
                    visibility = View.VISIBLE
                    setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_box_empty, 0, 0)
                    text = getString(R.string.no_projects)
                }
            }
        }
    }

    private suspend fun updateProjectView(project: Project) {
        withContext(Dispatchers.Main) {
            if (_binding == null)
                return@withContext
            listItems[project.info.id]?.tryUpdateView()
            updateTitle(projects)
        }
    }

    private suspend fun notifyProjectRemoved(projectId: ProjectID, projectName: String) {
        withContext(Dispatchers.Main) {
            if (_binding == null)
                return@withContext
            listItems -= projectId
            projects = projectManager.getProjects()
            submitList()
            updateTitle(projects)
            createSuccessPeek(translate {
                Locale.ENGLISH { "Successfully removed project $projectName" }
                Locale.KOREAN  { "프로젝트 $projectName 삭제 완료" }
            }, PeekAlert.Position.Bottom).peek()
        }
    }

    private suspend fun notifyProjectAdded(project: Project) {
        withContext(Dispatchers.Main) {
            if (_binding == null)
                return@withContext
            projects = projectManager.getProjects()
            submitList()
            updateTitle(projects)
            binding.recyclerViewProjectList.post {
                itemAdapter?.scrollTo(project)
            }
        }
    }

    private fun updateTitle(projects: List<Project>) {
        val activity = activity as? MainActivity ?: return

        val count = projects.count { it.info.isEnabled }
        activity.binding.statusText.text = getString(R.string.subtitle_projects).format(count)
    }

    @SuppressLint("CheckResult")
    private fun showProjectAlignDialog() =
        MaterialDialog(requireActivity(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            setCommonAttrsWithLifecycle()
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

    private fun getAlignByName(name: String): Align<Project>? =
        aligns.find { it.name == name }

    private fun sortedProjects(): List<Project> {
        val align = alignState.comparator
        val alignComparator = Comparator<Project> { a, b -> align.compare(a, b) }
            .let { if (isReversed) it.reversed() else it }
        val comparator = compareByDescending<Project> { it.info.isPinned }
            .let { if (isActiveFirst) it.thenByDescending { project -> project.isCompiled && project.info.isEnabled } else it }
            .then(alignComparator)

        val query = searchQuery
        return projects
            .filter { query.isEmpty() || it.info.name.contains(query, ignoreCase = true) }
            .sortedWith(comparator)
    }

    private fun submitList() {
        val weakThis = WeakReference<Fragment>(this)
        listItems.keys.retainAll(projects.map { it.info.id }.toSet())
        val items = sortedProjects().map { project ->
            listItems.getOrPut(project.info.id) { ProjectListItem(weakThis) }
                .withProject(project)
        }
        itemAdapter?.set(items)
        updateEmptyText(isFiltered = searchQuery.isNotEmpty(), isEmpty = items.isEmpty())
    }

    private fun updateAlignChip() {
        binding.alignProject.text = getString(R.string.aligned_by)
            .format(if (isReversed) alignState.reversedName else alignState.name)
        binding.alignProject.setChipIconResource(alignState.icon)
    }

    private fun update() {
        submitList()
        updateAlignChip()

        GlobalConfig.edit {
            getDefaultCategory().apply {
                set(CONFIG_PROJECTS_ALIGN, alignState.name)
                set(CONFIG_PROJECTS_REVERSED, isReversed.toString())
                set(CONFIG_PROJECTS_ACTIVE_FIRST, isActiveFirst.toString())
            }
        }
    }

    private fun ItemAdapter<ProjectListItem>.scrollTo(project: Project) {
        val index = getAdapterPosition(project.info.id.getLongHash())
        if (index == -1)
            return
        binding.recyclerViewProjectList.postDelayed({
            val scrollIdx = min(index + 2, adapterItemCount - 1)
            _binding?.recyclerViewProjectList?.smoothScrollToPosition(scrollIdx)
        }, 500)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (updateReceiveScope != null) {
            updateReceiveScope!!.cancel()
            updateReceiveScope = null
        }
        itemAdapter = null
        listItems.clear()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        updateTitle(projects)
    }

    companion object {
        @JvmStatic
        private val ALIGN_GANADA: Align<Project> = Align(
            name = "가나다 순",
            reversedName = "가나다 역순",
            icon = R.drawable.ic_round_sort_by_alpha_24,
            comparator = compareBy(String.CASE_INSENSITIVE_ORDER) { it.info.name }
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