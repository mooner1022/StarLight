
package com.mooner.starlight.ui.home

import android.annotation.SuppressLint
import android.content.res.ColorStateList
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
import com.mooner.starlight.plugincore.core.GeneralConfig
import com.mooner.starlight.plugincore.logger.LogType
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.theme.ThemeManager
import com.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import com.mooner.starlight.utils.Utils
import jp.wasabeef.recyclerview.animators.FadeInLeftAnimator
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

        val theme = ThemeManager.getCurrentTheme(requireContext())
        binding.homeInnerLayout.backgroundTintList = ColorStateList.valueOf(theme.background.toInt())

        //val allProjectsCount = Session.getProjectLoader().getProjects().size
        val activeProjects = Session.getProjectLoader().getEnabledProjects()

        val logs = Logger.filterNot(LogType.DEBUG)
        val adapter = LogsRecyclerViewAdapter(requireContext())

        if (logs.isNotEmpty()) {
            val layoutManager = LinearLayoutManager(requireContext()).apply {
                reverseLayout = true
                stackFromEnd = true
            }
            adapter.data = logs.subList(logs.size - min(LOGS_MAX_SIZE, logs.size), logs.size).toMutableList()
            binding.rvLogs.itemAnimator = FadeInLeftAnimator()
            binding.rvLogs.layoutManager = layoutManager
            binding.rvLogs.adapter = adapter
            binding.rvLogs.visibility = View.VISIBLE
            binding.textViewNoLogsYet.visibility = View.GONE
            adapter.notifyItemRangeInserted(0, min(LOGS_MAX_SIZE, logs.size))
        }

        Logger.bindListener {
            if (it.type != LogType.DEBUG) {
                adapter.pushLog(it, LOGS_MAX_SIZE)
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}