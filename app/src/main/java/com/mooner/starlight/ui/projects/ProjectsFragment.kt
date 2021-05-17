package com.mooner.starlight.ui.projects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BasicGridItem
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.gridItems
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.databinding.FragmentProjectsBinding
import com.mooner.starlight.models.Align
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.core.GeneralConfig
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.utils.Utils
import jp.wasabeef.recyclerview.animators.SlideInLeftAnimator

class ProjectsFragment : Fragment() {

    private lateinit var projectsViewModel: ProjectsViewModel
    private var _binding: FragmentProjectsBinding? = null
    private val binding get() = _binding!!
    private lateinit var projects: List<Project>
    private lateinit var recyclerAdapter: ProjectListAdapter
    //private val updateScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private val aligns = arrayOf(
        ALIGN_GANADA,
        //ALIGN_GANADA_INVERTED,
        ALIGN_DATE,
        //ALIGN_DATE_INVERTED,
        ALIGN_COMPILED,
        //ALIGN_NOT_COMPILED
    )
    private var alignState: Align = getAlignByName(Session.getGeneralConfig()[GeneralConfig.CONFIG_PROJECTS_ALIGN, DEFAULT_ALIGN.name])?: DEFAULT_ALIGN
    private var isReversed: Boolean = Session.getGeneralConfig()[GeneralConfig.CONFIG_PROJECTS_REVERSED,"false"].toBoolean()
    private var isActiveFirst: Boolean = Session.getGeneralConfig()[GeneralConfig.CONFIG_PROJECTS_ACTIVE_FIRST,"false"].toBoolean()
    
    companion object {
        private val ALIGN_GANADA = Align(
            name = "가나다 순",
            reversedName = "가나다 역순",
            icon = R.drawable.ic_round_sort_by_alpha_24,
            sort = { list, activeFirst ->
                val comparable = if (activeFirst) {
                    compareBy<Project>({ it.config.name }, { it.config.isEnabled })
                } else {
                    compareBy { it.config.name }
                }
                list.sortedWith(comparable)
            }
        )
        //private const val ALIGN_GANADA_INVERTED = "가나다 역순"
        private val ALIGN_DATE = Align(
            name = "생성일 순",
            reversedName = "생성일 역순",
            icon = R.drawable.ic_baseline_edit_calendar_24,
            sort = { list, activeFirst ->
                val comparable = if (activeFirst) {
                    compareBy<Project>({ it.config.createdMillis }, { it.config.isEnabled })
                } else {
                    compareBy { it.config.createdMillis }
                }
                list.sortedWith(comparable).asReversed()
            }
        )
        //private const val ALIGN_DATE_INVERTED = "생성일 역순"
        private val ALIGN_COMPILED = Align(
            name = "컴파일 순",
            reversedName = "미 컴파일 순",
            icon = R.drawable.ic_round_refresh_24,
            sort = { list, activeFirst ->
                val comparable = if (activeFirst) {
                    compareBy<Project>({ it.isCompiled }, { it.config.isEnabled })
                } else {
                    compareBy { it.isCompiled }
                }
                list.sortedWith(comparable).asReversed()
            }
        )
        //private const val ALIGN_NOT_COMPILED = "미컴파일 순"
        
        //private const val ALIGN_ = " 순"
        private val DEFAULT_ALIGN = ALIGN_GANADA
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectsBinding.inflate(inflater, container, false)
        projectsViewModel =
                ViewModelProvider(this).get(ProjectsViewModel::class.java)

        MainActivity.setToolbarText("Projects")
        val rvProjectList: RecyclerView = binding.recyclerViewProjectList

        val cvProjectAlign: CardView = binding.cardViewProjectAlign
        cvProjectAlign.setOnClickListener {
            MaterialDialog(requireContext(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                cornerRadius(25f)
                gridItems(getGridItems(*aligns)) { dialog, _, item ->
                    alignState = getAlignByName(item.title)?: DEFAULT_ALIGN
                    isReversed = dialog.findViewById<CheckBox>(R.id.checkBoxAlignReversed).isChecked
                    isActiveFirst = dialog.findViewById<CheckBox>(R.id.checkBoxAlignActiveFirst).isChecked
                    update()
                }
                //customView(R.layout.dialog_align_state)
                customView(R.layout.dialog_align_state)
                findViewById<CheckBox>(R.id.checkBoxAlignReversed).isChecked = isReversed
                findViewById<CheckBox>(R.id.checkBoxAlignActiveFirst).isChecked = isActiveFirst
            }
        }

        binding.textViewProjectAlignState.text = Utils.formatStringRes(R.string.project_align_state, mapOf("state" to alignState.name))
        binding.imageViewProjectAlignState.setImageResource(alignState.icon)

        val fab: FloatingActionButton = MainActivity.fab
        fab.show()
        MainActivity.reloadText()
        recyclerAdapter = ProjectListAdapter(requireContext())
        projectsViewModel.data.observe(viewLifecycleOwner) {
            projects = it
        }
        projects = Session.getProjectLoader().getProjects()
        recyclerAdapter.data = if (isReversed) alignState.sort(projects, isActiveFirst).asReversed() else alignState.sort(projects, isActiveFirst)
        recyclerAdapter.notifyItemRangeInserted(0, recyclerAdapter.data.size)

        val layoutManager = LinearLayoutManager(requireContext())
        rvProjectList.itemAnimator = SlideInLeftAnimator()
        rvProjectList.layoutManager = layoutManager
        rvProjectList.adapter = recyclerAdapter
        return binding.root
    }

    private fun getAlignByName(name: String): Align? {
        return aligns.find { it.name == name }
    }

    private fun getGridItems(vararg items: Align): List<BasicGridItem> {
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

    private fun update(align: Align = alignState, isReversed: Boolean = this.isReversed, activeFirst: Boolean = this.isActiveFirst) {
        binding.textViewProjectAlignState.text = Utils.formatStringRes(R.string.project_align_state, mapOf("state" to align.name))
        binding.imageViewProjectAlignState.setImageResource(align.icon)
        reloadList(if (isReversed) align.sort(projects, activeFirst).asReversed() else align.sort(projects, activeFirst))
        Session.getGeneralConfig().also {
            it[GeneralConfig.CONFIG_PROJECTS_ALIGN] = align.name
            it[GeneralConfig.CONFIG_PROJECTS_REVERSED] = isReversed.toString()
            it[GeneralConfig.CONFIG_PROJECTS_ACTIVE_FIRST] = activeFirst.toString()
            it.push()
            println("push!")
        }
    }
}