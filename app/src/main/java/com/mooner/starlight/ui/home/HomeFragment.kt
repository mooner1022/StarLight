
package com.mooner.starlight.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.databinding.FragmentHomeBinding
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
    private lateinit var mAdapter: LogsRecyclerViewAdapter

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

        val logs = Logger.filterNot(LogType.DEBUG)
        mAdapter = LogsRecyclerViewAdapter(requireContext())

        if (logs.isNotEmpty()) {
            val mLayoutManager = LinearLayoutManager(requireContext()).apply {
                reverseLayout = true
                stackFromEnd = true
            }
            mAdapter.data = logs.subList(logs.size - min(LOGS_MAX_SIZE, logs.size), logs.size).toMutableList()
            binding.rvLogs.apply {
                itemAnimator = FadeInLeftAnimator()
                layoutManager = mLayoutManager
                adapter = mAdapter
                visibility = View.VISIBLE
            }
            binding.textViewNoLogsYet.visibility = View.GONE
            mAdapter.notifyItemRangeInserted(0, min(LOGS_MAX_SIZE, logs.size))
        }

        bindLogger(mAdapter)

        binding.projectsRecyclerView

        binding.tvMoreLogs.setOnClickListener {
            Utils.showLogsDialog(requireContext())
        }

        binding.buttonMoreLogs.setOnClickListener {
            Utils.showLogsDialog(requireContext())
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

        bindLogger(mAdapter)
    }

    override fun onPause() {
        super.onPause()
        if (uptimeTimer != null) {
            uptimeTimer!!.cancel()
            uptimeTimer = null
        }

        unbindLogger()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (uptimeTimer != null) {
            uptimeTimer!!.cancel()
            uptimeTimer = null
        }
        unbindLogger()
        _binding = null
    }

    private fun bindLogger(mAdapter: LogsRecyclerViewAdapter) {
        Logger.bindListener(T) {
            if (it.type != LogType.DEBUG) {
                mAdapter.pushLog(it, LOGS_MAX_SIZE)
            }
        }
    }

    private fun unbindLogger() {
        Logger.unbindListener(T)
    }
}