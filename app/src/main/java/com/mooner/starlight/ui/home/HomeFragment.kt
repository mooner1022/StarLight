
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
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.mooner.starlight.databinding.FragmentHomeBinding
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.widget.Widget
import com.mooner.starlight.ui.widget.config.WidgetConfigActivity
import com.mooner.starlight.ui.widget.config.WidgetsAdapter
import com.mooner.starlight.utils.LAYOUT_DEFAULT
import com.mooner.starlight.utils.LAYOUT_TABLET
import com.mooner.starlight.utils.WIDGET_DEF_STRING
import com.mooner.starlight.utils.layoutMode
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

        val context = requireContext()

        widgetsAdapter = WidgetsAdapter(context).apply {
            data = getWidgets()
            notifyAllItemInserted()
        }

        val mLayoutManager = when(context.layoutMode) {
            LAYOUT_DEFAULT -> LinearLayoutManager(context)
            LAYOUT_TABLET -> StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            else -> LinearLayoutManager(context)
        }

        with(binding.widgets) {
            itemAnimator = FadeInUpAnimator()
            layoutManager = mLayoutManager
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

    private fun getWidgets(): List<Widget> {
        val widgetIds: List<String> = Session.json.decodeFromString(Session.globalConfig.getCategory("widgets").getString("ids", WIDGET_DEF_STRING))
        val widgets: MutableList<Widget> = mutableListOf()
        for (id in widgetIds) {
            with(Session.widgetManager.getWidgetById(id)) {
                if (this != null)
                    widgets += this
                else
                    Logger.w(HomeFragment::class.simpleName, "Skipping unknown widget: $id")
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
        _binding = null
    }
}