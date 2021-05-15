package com.mooner.starlight.ui.projects

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.databinding.FragmentProjectsBinding
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.core.GeneralConfig
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProjectsFragment : Fragment() {

    private lateinit var projectsViewModel: ProjectsViewModel
    private var _binding: FragmentProjectsBinding? = null
    private val binding get() = _binding!!
    private lateinit var projects: List<Project>
    private lateinit var recyclerAdapter: ProjectListAdapter
    private val updateScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    private val items = getGridItems(
        ALIGN_GANADA,
        ALIGN_GANADA_INVERTED,
        ALIGN_DATE,
        ALIGN_DATE_INVERTED,
        ALIGN_COMPILED,
        ALIGN_NOT_COMPILED
    )
    private var alignState: String = Session.getGeneralConfig()[GeneralConfig.CONFIG_PROJECTS_ALIGN, DEFAULT_ALIGN]
    
    companion object {
        private const val ALIGN_GANADA = "가나다 순"
        private const val ALIGN_GANADA_INVERTED = "가나다 역순"
        private const val ALIGN_DATE = "생성일 순"
        private const val ALIGN_DATE_INVERTED = "생성일 역순"
        private const val ALIGN_COMPILED = "컴파일 순"
        private const val ALIGN_NOT_COMPILED = "미컴파일 순"
        
        //private const val ALIGN_ = " 순"
        private const val DEFAULT_ALIGN = ALIGN_GANADA
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
                gridItems(items) { _, _, item ->
                    alignState = item.title
                    update()
                }
            }
        }

        binding.textViewProjectAlignState.text = Utils.formatStringRes(R.string.project_align_state, mapOf("state" to alignState))
        binding.imageViewProjectAlignState.setImageResource(getIcon(alignState))

        val fab: FloatingActionButton = MainActivity.fab
        fab.show()
        MainActivity.reloadText()
        recyclerAdapter = ProjectListAdapter(requireContext())
        projectsViewModel.data.observe(viewLifecycleOwner) {
            projects = it
            recyclerAdapter.data = align(alignState)
            Handler(Looper.getMainLooper()).post {
                recyclerAdapter.notifyDataSetChanged()
            }
        }

        val layoutManager = LinearLayoutManager(requireContext())
        rvProjectList.layoutManager = layoutManager
        rvProjectList.adapter = recyclerAdapter
        return binding.root
    }

    private fun align(state: String): List<Project> {
        return when(state) {
            ALIGN_GANADA -> {
                projects.sortedBy { it.config.name }
            }
            ALIGN_GANADA_INVERTED -> {
                projects.sortedByDescending { it.config.name }
            }
            ALIGN_DATE -> {
                projects.sortedByDescending { it.config.createdMillis }
            }
            ALIGN_DATE_INVERTED -> {
                projects.sortedBy { it.config.createdMillis }
            }
            ALIGN_COMPILED -> {
                projects.sortedByDescending { it.isCompiled }
            }
            ALIGN_NOT_COMPILED -> {
                projects.sortedBy { it.isCompiled }
            }
            else -> {
                align(DEFAULT_ALIGN)
            }
        }
    }

    private fun getGridItems(vararg items: String): List<BasicGridItem> {
        val list: MutableList<BasicGridItem> = mutableListOf()
        for (item in items) {
            list.add(
                BasicGridItem(
                    getIcon(item),
                    item
                )
            )
        }
        return list
    }

    private fun getIcon(name: String): Int {
        return when(name) {
            ALIGN_GANADA, ALIGN_GANADA_INVERTED -> R.drawable.ic_round_sort_by_alpha_24
            ALIGN_DATE, ALIGN_DATE_INVERTED -> R.drawable.ic_baseline_edit_calendar_24
            ALIGN_COMPILED, ALIGN_NOT_COMPILED -> R.drawable.ic_round_refresh_24
            else -> getIcon(DEFAULT_ALIGN)
        }
    }

    private fun update() {
        binding.textViewProjectAlignState.text = Utils.formatStringRes(R.string.project_align_state, mapOf("state" to alignState))
        binding.imageViewProjectAlignState.setImageResource(getIcon(alignState))
        recyclerAdapter.data = align(alignState)
        Handler(Looper.getMainLooper()).post {
            recyclerAdapter.notifyDataSetChanged()
        }
        Session.getGeneralConfig().also {
            it[GeneralConfig.CONFIG_PROJECTS_ALIGN] = alignState
            it.push()
        }
    }
}