
package com.mooner.starlight.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.databinding.FragmentHomeBinding
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import jp.wasabeef.recyclerview.animators.SlideInLeftAnimator
import java.lang.Integer.min

class HomeFragment : Fragment() {
    private lateinit var homeViewModel: HomeViewModel
    private var _binding: FragmentHomeBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    companion object {
        private const val LOGS_MAX_SIZE = 5
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        homeViewModel =
                ViewModelProvider(this).get(HomeViewModel::class.java)
        val fab: FloatingActionButton = MainActivity.fab
        if (fab.isVisible) {
            fab.hide()
        }
        //val textView: TextView = root.findViewById(R.id.textViewLogs)
        //root.switchAllPower.setOnCheckedChangeListener { _, isChecked ->
        //    root.cardViewAllPower.setCardBackgroundColor(root.context.getColor(if (isChecked) R.color.cardview_enabled else R.color.cardview_disabled))
        //}
        MainActivity.setToolbarText(requireContext().getString(R.string.app_name))
        MainActivity.reloadText(requireContext().getString(R.string.app_version))

        //val allProjectsCount = Session.getProjectLoader().getProjects().size
        val activeProjectsCount = Session.getProjectLoader().getEnabledProjects().size
        binding.textViewHomeBotStatus.text = "${activeProjectsCount}개의 프로젝트가 작동중이에요."

        binding.cardViewManageProject.setOnClickListener {
            //Session.getLogger().e("TEST", IllegalStateException("TEXT Exception").toString())
            Navigation.findNavController(it).navigate(R.id.nav_projects)
        }

        binding.cardViewManagePlugin.setOnClickListener {
            Navigation.findNavController(it).navigate(R.id.nav_plugins)
        }
        var count = 0
        binding.buttonMoreLogs.setOnClickListener {
            Session.getLogger().i(javaClass.simpleName, "TEST Log: INFO! :) $count")
            Session.getLogger().w(javaClass.simpleName, "TEST Log: WARNING! :) $count")
            Session.getLogger().d(javaClass.simpleName, "TEST Log: DEBUG! :) $count")
            count ++
        }

        val logs = Session.getLogger().logs
        val adapter = LogsRecyclerViewAdapter(requireContext())
        if (logs.isNotEmpty()) {
            val layoutManager = LinearLayoutManager(requireContext()).apply {
                reverseLayout = true
                stackFromEnd = true
            }
            adapter.data = logs.subList(logs.size - min(LOGS_MAX_SIZE, logs.size), logs.size).toMutableList()
            binding.rvLogs.itemAnimator = SlideInLeftAnimator()
            binding.rvLogs.layoutManager = layoutManager
            binding.rvLogs.adapter = adapter
            binding.rvLogs.visibility = View.VISIBLE
            binding.textViewNoLogsYet.visibility = View.GONE
            adapter.notifyItemRangeInserted(0, min(LOGS_MAX_SIZE, logs.size))
        }

        Session.getLogger().bindListener {
            adapter.pushLog(it, LOGS_MAX_SIZE)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}