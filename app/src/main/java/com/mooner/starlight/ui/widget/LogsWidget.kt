package com.mooner.starlight.ui.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.widget.Widget
import com.mooner.starlight.plugincore.widget.WidgetSize
import com.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import com.mooner.starlight.utils.Utils
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator
import java.lang.Integer.min

class LogsWidget: Widget() {

    companion object {
        private const val LOGS_MAX_SIZE = 3
        private const val T = "LogsWidget"
    }

    override val id: String = "widget_logs"
    override val name: String = "로그"
    override val size: WidgetSize = WidgetSize.XLarge

    private lateinit var mAdapter: LogsRecyclerViewAdapter

    override fun onCreateWidget(view: View) {
        val context = view.context
        LayoutInflater.from(context).inflate(R.layout.widget_logs, view as ViewGroup, true)
        with(view) {
            val tvMoreLogs: TextView = findViewById(R.id.tvMoreLogs)
            tvMoreLogs.setOnClickListener {
                Utils.showLogsDialog(context)
            }

            val buttonMoreLogs: ImageButton = findViewById(R.id.buttonMoreLogs)
            buttonMoreLogs.setOnClickListener {
                Utils.showLogsDialog(context)
            }

            val rvLogs: RecyclerView = findViewById(R.id.rvLogs)
            val textViewNoLogsYet: TextView = findViewById(R.id.textViewNoLogsYet)
            val logs = Logger.logs
            mAdapter = LogsRecyclerViewAdapter(context)

            if (logs.isNotEmpty()) {
                val mLayoutManager = LinearLayoutManager(context).apply {
                    reverseLayout = true
                    stackFromEnd = true
                }
                mAdapter.data = logs.subList(logs.size - min(LOGS_MAX_SIZE, logs.size), logs.size).toMutableList()
                rvLogs.apply {
                    itemAnimator = FadeInUpAnimator()
                    layoutManager = mLayoutManager
                    adapter = mAdapter
                    visibility = View.VISIBLE
                }
                textViewNoLogsYet.visibility = View.GONE
                mAdapter.notifyItemRangeInserted(0, min(LOGS_MAX_SIZE, logs.size))
            }

            bindLogger()
        }
    }

    /*
    override fun onPauseWidget() {
        super.onPauseWidget()
        unbindLogger()
    }

    override fun onResumeWidget() {
        super.onResumeWidget()
        if (this::mAdapter.isInitialized) {
            val logs = Logger.logs
            Logger.e("onResumeWidget")
            if (mAdapter.data.size != logs.size) {
                val missedLogs = logs.drop(mAdapter.data.size)
                for (data in missedLogs) {
                    mAdapter.pushLog(data, LOGS_MAX_SIZE)
                }
            }
            bindLogger()
        }
    }
     */

    override fun onDestroyWidget() {
        super.onDestroyWidget()
        unbindLogger()
    }

    override fun onCreateThumbnail(view: View) {
        LayoutInflater.from(view.context).inflate(R.layout.widget_logs, view as ViewGroup, true)
    }

    private fun bindLogger() {
        Logger.bindListener(T) {
            mAdapter.pushLog(it, LOGS_MAX_SIZE)
        }
    }

    private fun unbindLogger() {
        Logger.unbindListener(T)
    }
}