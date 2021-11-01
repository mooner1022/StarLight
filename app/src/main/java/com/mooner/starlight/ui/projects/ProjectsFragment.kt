package com.mooner.starlight.ui.projects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BasicGridItem
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.gridItems
import com.afollestad.materialdialogs.customview.customView
import com.mooner.starlight.R
import com.mooner.starlight.databinding.FragmentProjectsBinding
import com.mooner.starlight.models.Align
import com.mooner.starlight.plugincore.core.Session.generalConfig
import com.mooner.starlight.plugincore.core.Session.projectManager
import com.mooner.starlight.plugincore.project.Project
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator

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
                    compareBy<Project>({ !it.info.isEnabled }, { it.info.name })
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
                    compareBy<Project>({ !it.info.isEnabled }, { it.info.createdMillis })
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
                    compareBy<Project>({ !it.info.isEnabled }, { !it.isCompiled })
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

    private lateinit var projects: List<Project>
    private var recyclerAdapter: ProjectListAdapter? = null
    private val aligns = arrayOf(
        ALIGN_GANADA,
        ALIGN_DATE,
        ALIGN_COMPILED
    )
    private var alignState: Align<Project> = getAlignByName(
        generalConfig[CONFIG_PROJECTS_ALIGN, DEFAULT_ALIGN.name]
    )?: DEFAULT_ALIGN
    private var isReversed: Boolean = generalConfig[CONFIG_PROJECTS_REVERSED, "false"].toBoolean()
    private var isActiveFirst: Boolean = generalConfig[CONFIG_PROJECTS_ACTIVE_FIRST, "false"].toBoolean()

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

        recyclerAdapter = ProjectListAdapter(requireContext())

        projects = projectManager.getProjects()
        recyclerAdapter!!.data = sortData()
        recyclerAdapter!!.notifyItemRangeInserted(0, recyclerAdapter!!.data.size)

        with(binding.recyclerViewProjectList) {
            itemAnimator = FadeInUpAnimator()
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recyclerAdapter
        }

        bindListener()
        return binding.root
    }

    private fun getAlignByName(name: String): Align<Project>? = aligns.find { it.name == name }

    private fun Array<Align<Project>>.toGridItems(): List<BasicGridItem> = this.map { item ->
        BasicGridItem(
            iconRes = item.icon,
            title = item.name
        )
    }

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

        generalConfig.apply {
            set(CONFIG_PROJECTS_ALIGN, alignState.name)
            set(CONFIG_PROJECTS_REVERSED, isReversed.toString())
            set(CONFIG_PROJECTS_ACTIVE_FIRST, isActiveFirst.toString())
            push()
        }
    }

    override fun onPause() {
        super.onPause()
        unbindListener()
    }

    override fun onResume() {
        super.onResume()
        bindListener()
        if (this.projects.size != projectManager.getProjects().size) {
            this.projects = projectManager.getProjects()
            update()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unbindListener()
        recyclerAdapter = null
    }

    private fun bindListener() {
        projectManager.bindListener(T) { projects ->
            this.projects = projects
            update()
        }
    }

    private fun unbindListener() {
        projectManager.unbindListener(T)
    }
}