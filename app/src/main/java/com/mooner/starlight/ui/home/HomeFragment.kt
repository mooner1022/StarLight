
package com.mooner.starlight.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.mooner.starlight.databinding.FragmentHomeBinding
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.widget.Widget
import com.mooner.starlight.ui.widget.config.WidgetConfigActivity
import com.mooner.starlight.ui.widget.config.WidgetsAdapter
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator
import kotlinx.serialization.decodeFromString

class HomeFragment : Fragment() {

    private lateinit var resultLauncher: ActivityResultLauncher<Intent>
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var widgetsAdapter: WidgetsAdapter? = null

    /*
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
     */

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        /*
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
        */

        widgetsAdapter = WidgetsAdapter(requireContext()).apply {
            data = getWidgets()
            notifyAllItemInserted()
        }

        with(binding.widgets) {
            itemAnimator = FadeInUpAnimator()
            layoutManager = LinearLayoutManager(requireContext())
            adapter = widgetsAdapter
        }

        resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == WidgetConfigActivity.RESULT_EDITED) {
                widgetsAdapter!!.apply {
                    onDestroy()
                    notifyItemRangeRemoved(0, data.size)
                    data = getWidgets()
                    notifyItemRangeInserted(0, data.size)
                }
                Logger.v("List updated")
            }
        }
        binding.cardViewConfigWidget.setOnClickListener {
            val intent = Intent(requireActivity(), WidgetConfigActivity::class.java)
            resultLauncher.launch(intent)
        }

        return binding.root
    }
    /*
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
    */

    private fun getWidgets(): List<Widget> {
        val widgetIds: List<String> = Session.json.decodeFromString(Session.globalConfig.getCategory("widgets").getString("ids", "[]"))
        Logger.v("ids= $widgetIds")
        val widgets: MutableList<Widget> = mutableListOf()
        for (id in widgetIds) {
            with(Session.widgetManager.getWidgetById(id)) {
                if (this != null)
                    widgets += this
            }
        }
        return widgets
    }

    override fun onPause() {
        super.onPause()
        widgetsAdapter?.onPause()
    }

    override fun onResume() {
        super.onResume()
        widgetsAdapter?.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        widgetsAdapter?.onDestroy()
        widgetsAdapter = null
        /*
        if (uptimeTimer != null) {
            uptimeTimer!!.cancel()
            uptimeTimer = null
        }
        unbindLogger()
        */
        _binding = null
    }
}