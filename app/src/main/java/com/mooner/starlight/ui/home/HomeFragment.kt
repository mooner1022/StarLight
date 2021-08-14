
package com.mooner.starlight.ui.home

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.databinding.FragmentHomeBinding
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.logger.LogType
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import com.mooner.starlight.utils.Utils
import jp.wasabeef.recyclerview.animators.FadeInLeftAnimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Integer.min
import java.util.*

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var uptimeTimer: Timer? = null
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val updateUpTimeTask: TimerTask
        get() = object: TimerTask() {
            override fun run() {
                val diffMillis = System.currentTimeMillis() - ApplicationSession.initMillis
                val formatStr = Utils.formatTime(diffMillis)
                mainScope.launch {
                    binding.uptimeText.setText(formatStr)
                }
            }
        }

    companion object {
        private const val LOGS_MAX_SIZE = 5
        private const val T = "HomeFragment"
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        //val allProjectsCount = Session.getProjectLoader().getProjects().size
        val activeProjects = Session.projectLoader.getEnabledProjects()
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

        Logger.bindListener(T) {
            if (it.type != LogType.DEBUG) {
                adapter.pushLog(it, LOGS_MAX_SIZE)
            }
        }

        binding.uptimeText.setInAnimation(requireContext(), R.anim.text_fade_in)
        binding.uptimeText.setOutAnimation(requireContext(), R.anim.text_fade_out)

        uptimeTimer = Timer()
        uptimeTimer!!.schedule(updateUpTimeTask, 0, 1000)

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (uptimeTimer == null) {
            uptimeTimer = Timer()
            uptimeTimer!!.schedule(updateUpTimeTask, 0, 1000)
        }
    }

    override fun onPause() {
        super.onPause()
        if (uptimeTimer != null) {
            uptimeTimer!!.cancel()
            uptimeTimer = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (uptimeTimer != null) {
            uptimeTimer!!.cancel()
            uptimeTimer = null
        }
        Logger.unbindListener(T)
        _binding = null
    }
}