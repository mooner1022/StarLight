package dev.mooner.starlight.ui.widget

import android.animation.LayoutTransition
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.widget.Widget
import dev.mooner.starlight.plugincore.widget.WidgetSize
import dev.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import dev.mooner.starlight.utils.showLogsDialog
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Integer.min

class LogsWidget: Widget() {

    companion object {
        private const val LOGS_MAX_SIZE = 3
        private const val T = "LogsWidget"
    }

    override val id: String = "widget_logs"
    override val name: String = "로그"
    override val size: WidgetSize = WidgetSize.Wrap

    private var pauseUpdate: Boolean = false
    private var stackedLogs: MutableList<LogData> = mutableListOf()

    private var mAdapter: LogsRecyclerViewAdapter? = null
    private var mThumbnailAdapter: LogsRecyclerViewAdapter? = null
    private var mView: View? = null

    override fun onCreateWidget(view: View) {
        mView = view
        val context = view.context
        LayoutInflater.from(context).inflate(R.layout.widget_logs, view as ViewGroup, true)
        with(view) {
            val tvMoreLogs: TextView = findViewById(R.id.tvMoreLogs)
            tvMoreLogs.setOnClickListener {
                showLogsDialog(context)
            }

            val buttonMoreLogs: ImageButton = findViewById(R.id.buttonMoreLogs)
            buttonMoreLogs.setOnClickListener {
                showLogsDialog(context)
            }

            layoutTransition = LayoutTransition()
            val rvLogs: RecyclerView = findViewById(R.id.rvLogs)
            val textViewNoLogsYet: TextView = findViewById(R.id.textViewNoLogsYet)
            val logs = Logger.logs
            mAdapter = LogsRecyclerViewAdapter(context)

            if (logs.isNotEmpty()) {
                val mLayoutManager = LinearLayoutManager(context).apply {
                    reverseLayout = true
                    stackFromEnd = true
                }
                mAdapter!!.apply {
                    data = logs.subList(logs.size - min(LOGS_MAX_SIZE, logs.size), logs.size).toMutableList()
                    notifyItemRangeInserted(0, min(LOGS_MAX_SIZE, logs.size))
                }
                rvLogs.apply {
                    itemAnimator = FadeInUpAnimator()
                    layoutManager = mLayoutManager
                    adapter = mAdapter
                    visibility = View.VISIBLE
                }
                mView!!.updateHeight()
                textViewNoLogsYet.visibility = View.GONE
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

    override fun onResumeWidget() {
        super.onResumeWidget()
        pauseUpdate = false
        if (stackedLogs.isNotEmpty() && mAdapter != null) {
            val logs = if (stackedLogs.size > 3) {
                stackedLogs.drop(stackedLogs.size - 3)
            } else stackedLogs

            for (logData in logs) {
                mAdapter!!.pushLog(logData, LOGS_MAX_SIZE)
            }
            stackedLogs.clear()
        }
    }

    override fun onPauseWidget() {
        super.onPauseWidget()
        pauseUpdate = true
    }

    override fun onDestroyWidget() {
        super.onDestroyWidget()
        mView = null
        unbindLogger()
        mAdapter = null
    }

    override fun onCreateThumbnail(view: View) {
        LayoutInflater.from(view.context).inflate(R.layout.widget_logs, view as ViewGroup, true)

        val dummyLog = LogData(
            type = LogType.DEBUG,
            message = "This is a test log.\nMessage is shown here."
        )

        with(view) {
            val rvLogs: RecyclerView = findViewById(R.id.rvLogs)
            val textViewNoLogsYet: TextView = findViewById(R.id.textViewNoLogsYet)
            val logs: List<LogData> = listOf(dummyLog, dummyLog, dummyLog)
            mThumbnailAdapter = LogsRecyclerViewAdapter(context, false)

            val mLayoutManager = LinearLayoutManager(context).apply {
                reverseLayout = true
                stackFromEnd = true
            }
            mThumbnailAdapter!!.apply {
                data = logs.subList(logs.size - min(LOGS_MAX_SIZE, logs.size), logs.size).toMutableList()
                notifyItemRangeInserted(0, min(LOGS_MAX_SIZE, logs.size))
            }
            rvLogs.apply {
                layoutManager = mLayoutManager
                adapter = mThumbnailAdapter
                visibility = View.VISIBLE
            }
            view.updateHeight()
            textViewNoLogsYet.visibility = View.GONE

            bindLogger()
        }
    }

    override fun onDestroyThumbnail() {
        super.onDestroyThumbnail()
        mThumbnailAdapter = null
    }

    private fun bindLogger() {
        Logger.bindListener(T) {
            if (it.type == LogType.VERBOSE && !dev.mooner.starlight.plugincore.Session.globalConfig.getCategory("dev_mode_config").getBoolean("show_internal_log", false)) return@bindListener

            if (pauseUpdate) {
                stackedLogs += it
            } else {
                mAdapter?.pushLog(it, LOGS_MAX_SIZE)
                if (mView != null) {
                    CoroutineScope(Dispatchers.Main).launch {
                        mView!!.updateHeight()
                    }
                }
            }
        }
    }

    private fun unbindLogger() {
        Logger.unbindListener(T)
    }
}