package com.mooner.starlight.ui.projects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R

class ProjectsFragment : Fragment() {

    private lateinit var projectsViewModel: ProjectsViewModel

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        projectsViewModel =
                ViewModelProvider(this).get(ProjectsViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_projects, container, false)

        MainActivity.setToolbarText("Projects")
        val rvProjectList: RecyclerView = root.findViewById(R.id.recyclerViewProjectList)

        val fab: FloatingActionButton = MainActivity.fab
        fab.show()
        MainActivity.reloadText()
        val recyclerAdapter = ProjectListAdapter(root.context)
        projectsViewModel.data.observe(viewLifecycleOwner) {
            recyclerAdapter.data = it
            recyclerAdapter.notifyDataSetChanged()
        }

        val layoutManager = LinearLayoutManager(root.context)
        rvProjectList.layoutManager = layoutManager
        rvProjectList.adapter = recyclerAdapter
        return root
    }
}