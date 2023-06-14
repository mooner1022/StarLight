package dev.mooner.starlight.ui.widget

import android.animation.LayoutTransition
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.WidgetLogsBinding
import dev.mooner.starlight.logging.LogCollector
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.widget.Widget
import dev.mooner.starlight.plugincore.widget.WidgetSize
import dev.mooner.starlight.ui.logs.LogItem
import dev.mooner.starlight.utils.dropBefore
import dev.mooner.starlight.utils.showLogsDialog
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogsWidget: Widget(), OnClickListener {

    //private var _binding: WidgetLogsBinding? = null
    //private val binding get() = _binding!!
    private var itemAdapter: ItemAdapter<LogItem>? = null
    private var lastHash: Int? = null

    override val id: String = "widget_logs"
    override val name: String = "로그"
    override val size: WidgetSize = WidgetSize.Wrap

    private var mView: View? = null

    override fun onCreateWidget(view: View) {
        val context = view.context
        val binding = WidgetLogsBinding.inflate(LayoutInflater.from(context), view as ViewGroup, true)

        mView = view
        //LayoutInflater.from(context).inflate(R.layout.widget_logs, view as ViewGroup, true)

        createAdapter(binding.rvLogs)

        val logs = LogCollector.logs
        if (logs.isEmpty()) {
            binding.textViewNoLogsYet.visibility = View.VISIBLE
            binding.rvLogs.visibility = View.GONE
        } else {
            binding.textViewNoLogsYet.visibility = View.GONE
            binding.rvLogs.visibility = View.VISIBLE
        }

        logs.dropBefore(LOGS_MAX_SIZE)
            .map { v -> v.toLogItem() }
            .let(itemAdapter!!::set)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (itemAdapter == null)
                    createAdapter(binding.rvLogs)

                EventHandler.on(this, ::onLogCreated)
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (itemAdapter == null)
                    createAdapter(binding.rvLogs)

                logs.dropBefore(LOGS_MAX_SIZE)
                    .takeIf { logs -> logs[0].hashCode() != lastHash }
                    ?.map { v -> v.toLogItem() }
                    ?.let(itemAdapter!!::set)
            }
        }

        binding.root.layoutTransition = LayoutTransition()

        // Set click listeners
        binding.tvMoreLogs.setOnClickListener(this)
        binding.buttonMoreLogs.setOnClickListener(this)
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

    override fun onPauseWidget() {
        super.onPauseWidget()
        setLastHash()
    }

    override fun onDestroyWidget() {
        super.onDestroyWidget()
        setLastHash()
        mView = null
        itemAdapter = null
    }

    private fun setLastHash() {
        lastHash = itemAdapter?.getAdapterItem(0)?.logData?.hashCode()
    }

    override fun onCreateThumbnail(view: View) {
        val context = view.context
        val binding = WidgetLogsBinding.inflate(LayoutInflater.from(context), view as ViewGroup, true)

        binding.textViewNoLogsYet.visibility = View.GONE

        val dummyLog = LogData(
            type = LogType.INFO,
            message = translate {
                Locale.ENGLISH { "This is a dummy log for thumbnail.\nMessage is shown here." }
                Locale.KOREAN  { "이건 썸네일용 더미 로그에요.\n로그 메세지는 여기에 보여져요." }
            }
        ).toLogItem()

        itemAdapter = ItemAdapter()
        val fastAdapter = FastAdapter.with(itemAdapter!!)
        binding.rvLogs.init(fastAdapter)

        itemAdapter!!.add(dummyLog, dummyLog, dummyLog)
    }

    override fun onDestroyThumbnail() {
        super.onDestroyThumbnail()

        itemAdapter = null
    }

    override fun onClick(view: View) {
        val context = view.context
        when(view.id) {
            R.id.tvMoreLogs, R.id.buttonMoreLogs ->
                context.showLogsDialog()
        }
    }

    private fun createAdapter(recyclerView: RecyclerView) {
        itemAdapter = ItemAdapter()
        val fastAdapter = FastAdapter.with(itemAdapter!!)
        recyclerView.init(fastAdapter)
    }

    private fun RecyclerView.init(adapter: FastAdapter<LogItem>) {
        itemAnimator = FadeInUpAnimator()
        layoutManager = initLayoutManager(context)
        this.adapter = adapter
        visibility = View.VISIBLE
    }

    private fun initLayoutManager(context: Context): LayoutManager =
        LinearLayoutManager(context).apply {
            reverseLayout = true
            stackFromEnd = true
        }

    private fun LogData.toLogItem(): LogItem =
        LogItem().withLogData(this)

    private suspend fun onLogCreated(event: Events.Log.Create) {
        val log = event.log
        val showInternalLog = GlobalConfig
            .category("dev_mode_config")
            .getBoolean("show_internal_log", false)

        if (log.type == LogType.VERBOSE && !showInternalLog) return

        //mAdapter?.pushLog(log, LOGS_MAX_SIZE)
        // Avoid infinite-loop
        //println(itemAdapter)
        if (itemAdapter == null)
            return

        withContext(Dispatchers.Main) {
            if (itemAdapter!!.adapterItemCount >= LOGS_MAX_SIZE)
                itemAdapter!!.remove(0)
            itemAdapter!!.add(log.toLogItem())

            mView?.updateHeight()
        }
    }

    companion object {
        private const val LOGS_MAX_SIZE = 3
    }
}