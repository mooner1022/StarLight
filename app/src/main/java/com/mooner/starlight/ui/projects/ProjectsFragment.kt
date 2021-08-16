package com.mooner.starlight.ui.projects

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
import com.mooner.starlight.databinding.FragmentProjectsBinding
import com.mooner.starlight.models.Align
import com.mooner.starlight.plugincore.core.GeneralConfig
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.utils.Utils
import jp.wasabeef.recyclerview.animators.FadeInLeftAnimator

class ProjectsFragment : Fragment() {

    private var _binding: FragmentProjectsBinding? = null
    private val binding get() = _binding!!

    private lateinit var projects: List<Project>
    private lateinit var recyclerAdapter: ProjectListAdapter
    private val aligns = arrayOf(
        ALIGN_GANADA,
        ALIGN_DATE,
        ALIGN_COMPILED
    )
    private var alignState: Align<Project> = getAlignByName(
        Session.getGeneralConfig()
                [GeneralConfig.CONFIG_PROJECTS_ALIGN, DEFAULT_ALIGN.name]
    )?: DEFAULT_ALIGN
    private var isReversed: Boolean = Session.getGeneralConfig()[GeneralConfig.CONFIG_PROJECTS_REVERSED,"false"].toBoolean()
    private var isActiveFirst: Boolean = Session.getGeneralConfig()[GeneralConfig.CONFIG_PROJECTS_ACTIVE_FIRST,"false"].toBoolean()
    
    companion object {
        private const val T = "ProjectsFragment"

        private val ALIGN_GANADA = Align<Project>(
            name = "가나다 순",
            reversedName = "가나다 역순",
            icon = R.drawable.ic_round_sort_by_alpha_24,
            sort = { list, args ->
                val comparable = if (args.containsKey("activeFirst")) {
                    compareBy<Project>({ it.config.name }, { it.config.isEnabled })
                } else {
                    compareBy { it.config.name }
                }
                list.sortedWith(comparable)
            }
        )
        private val ALIGN_DATE = Align<Project>(
            name = "생성일 순",
            reversedName = "생성일 역순",
            icon = R.drawable.ic_baseline_edit_calendar_24,
            sort = { list, args ->
                val comparable = if (args.containsKey("activeFirst")) {
                    compareBy<Project>({ it.config.createdMillis }, { it.config.isEnabled })
                } else {
                    compareBy { it.config.createdMillis }
                }
                list.sortedWith(comparable).asReversed()
            }
        )
        private val ALIGN_COMPILED = Align<Project>(
            name = "컴파일 순",
            reversedName = "미 컴파일 순",
            icon = R.drawable.ic_round_refresh_24,
            sort = { list, args ->
                val comparable = if (args.containsKey("activeFirst")) {
                    compareBy<Project>({ it.isCompiled }, { it.config.isEnabled })
                } else {
                    compareBy { it.isCompiled }
                }
                list.sortedWith(comparable).asReversed()
            }
        )
        private val DEFAULT_ALIGN = ALIGN_GANADA
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectsBinding.inflate(inflater, container, false)

        binding.cardViewProjectAlign.setOnClickListener {
            MaterialDialog(requireContext(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                cornerRadius(25f)
                gridItems(getGridItems(*aligns)) { dialog, _, item ->
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

        binding.textViewProjectAlignState.text = Utils.formatStringRes(
            R.string.project_align_state,
            mapOf(
                "state" to if (isReversed) alignState.reversedName else alignState.name
            )
        )
        binding.imageViewProjectAlignState.setImageResource(alignState.icon)

        recyclerAdapter = ProjectListAdapter(requireContext())

        Session.projectLoader.bindListener(T) { projects ->
            this.projects = projects
            update()
        }

        val data = Session.projectLoader.loadProjects(false)
        this.projects = data
        update()

        projects = Session.projectLoader.getProjects()
        recyclerAdapter.data = if (isReversed) {
            alignState.sort(
                projects,
                mapOf(
                    "activeFirst" to isActiveFirst
                )
            ).asReversed()
        } else {
            alignState.sort(
                projects,
                mapOf(
                    "activeFirst" to isActiveFirst
                )
            )
        }
        recyclerAdapter.notifyItemRangeInserted(0, recyclerAdapter.data.size)

        with(binding.recyclerViewProjectList) {
            itemAnimator = FadeInLeftAnimator()
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recyclerAdapter
        }
        return binding.root
    }

    private fun getAlignByName(name: String): Align<Project>? {
        return aligns.find { it.name == name }
    }

    private fun getGridItems(vararg items: Align<Project>): List<BasicGridItem> {
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

    private fun reloadList(data: List<Project>) {
        val orgDataSize = recyclerAdapter.data.size
        recyclerAdapter.data = listOf()
        recyclerAdapter.notifyItemRangeRemoved(0, orgDataSize)
        recyclerAdapter.data = data
        recyclerAdapter.notifyItemRangeInserted(0, data.size)
    }

    private fun update(align: Align<Project> = alignState, isReversed: Boolean = this.isReversed, activeFirst: Boolean = this.isActiveFirst) {
        binding.textViewProjectAlignState.text = Utils.formatStringRes(
            R.string.project_align_state,
            mapOf(
                "state" to if (isReversed) align.reversedName else align.name
            )
        )
        binding.imageViewProjectAlignState.setImageResource(align.icon)
        reloadList(
            if (isReversed) {
                alignState.sort(
                    projects,
                    mapOf(
                        "activeFirst" to isActiveFirst
                    )
                ).asReversed()
            } else {
                alignState.sort(
                    projects,
                    mapOf(
                        "activeFirst" to isActiveFirst
                    )
                )
            }
        )
        Session.getGeneralConfig().also {
            it[GeneralConfig.CONFIG_PROJECTS_ALIGN] = align.name
            it[GeneralConfig.CONFIG_PROJECTS_REVERSED] = isReversed.toString()
            it[GeneralConfig.CONFIG_PROJECTS_ACTIVE_FIRST] = activeFirst.toString()
            it.push()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Session.projectLoader.unbindListener(T)
    }
}