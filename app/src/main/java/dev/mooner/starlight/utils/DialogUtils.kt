package dev.mooner.starlight.utils

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.setPeekHeight
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.maxkeppeler.sheets.color.ColorSheet
import com.maxkeppeler.sheets.core.Sheet
import com.maxkeppeler.sheets.core.SheetStyle
import com.maxkeppeler.sheets.info.InfoSheet
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.DialogLogsBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator
import kotlinx.coroutines.*

class CustomSheet: Sheet() {

    private lateinit var binding: DialogLogsBinding
    private val logUpdateJob = Job()

    override fun onCreateLayoutView(): View =
        DialogLogsBinding.inflate(LayoutInflater.from(activity)).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        style(SheetStyle.BOTTOM_SHEET)
        displayHandle(true)
        displayCloseButton(true)
        displayPositiveButton(false)
        width = resources.getDimensionPixelSize(R.dimen.dialog_width)

        val activity = requireActivity()

        val logs = Logger.logs
        val mAdapter = LogsRecyclerViewAdapter(activity).apply {
            data = logs.toMutableList()
        }
        val mLayoutManager = LinearLayoutManager(activity).apply {
            reverseLayout = true
            stackFromEnd = true
        }

        binding.rvLog.apply {
            itemAnimator = FadeInUpAnimator()
            layoutManager = mLayoutManager
            adapter = mAdapter
        }
        mAdapter.notifyItemRangeInserted(0, logs.size)

        CoroutineScope(Dispatchers.Main + logUpdateJob).launch {
            EventHandler.on<Events.Log.LogCreateEvent>(this) {
                if (log.type == LogType.VERBOSE && !Session.globalConfig.category("dev_mode_config").getBoolean("show_internal_log", false)) return@on
                mAdapter.pushLog(log)
                binding.rvLog.let { recycler ->
                    recycler.post {
                        recycler.smoothScrollToPosition(mAdapter.data.size - 1)
                    }
                }
            }
        }
        super.onViewCreated(view, savedInstanceState)
        (dialog as BottomSheetDialog?)?.behavior?.apply {
            setPeekHeight(PEEK_HEIGHT_AUTO, true)
            isFitToContents = false
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        (dialog as BottomSheetDialog?)?.behavior?.apply {
            state = STATE_HALF_EXPANDED
        }
        return super.onCreateDialog(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        logUpdateJob.cancel()
    }

    fun build(ctx: Context, width: Int? = null, func: CustomSheet.() -> Unit): CustomSheet {
        this.windowContext = ctx
        this.width = width
        this.func()
        return this
    }

    fun show(ctx: Context, width: Int? = null, func: CustomSheet.() -> Unit): CustomSheet {
        this.windowContext = ctx
        this.width = width
        this.func()
        this.show()
        return this
    }
}

fun Context.showLogsDialog() {
    /*
    val job = Job()

    val mLogs = if (Session.globalConfig.category("dev_mode_config").getBoolean("show_internal_log", false))
        Logger.logs
    else
        Logger.filterNot(LogType.VERBOSE)

    val mAdapter = LogsRecyclerViewAdapter(this).apply {
        data = mLogs.toMutableList()
    }
    val mLayoutManager = LinearLayoutManager(this).apply {
        reverseLayout = true
        stackFromEnd = true
    }
    return MaterialDialog(this, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
        if (this@showLogsDialog is AppCompatActivity)
            lifecycleOwner(this@showLogsDialog)
        cornerRadius(res = R.dimen.card_radius)
        //maxWidth(res = R.dimen.dialog_width)
        cancelOnTouchOutside(true)
        noAutoDismiss()
        title(text = context.getString(R.string.title_logs))
        customView(R.layout.dialog_logs)
        positiveButton(text = context.getString(R.string.close)) {
            dismiss()
        }

        val recycler: RecyclerView = findViewById(R.id.rvLog)
        recycler.apply {
            itemAnimator = FadeInUpAnimator()
            layoutManager = mLayoutManager
            adapter = mAdapter
        }
        mAdapter.notifyItemRangeInserted(0, mLogs.size)

        CoroutineScope(Dispatchers.Main + job).launch {
            EventHandler.on<Events.Log.LogCreateEvent>(this) {
                if (log.type == LogType.VERBOSE && !Session.globalConfig.category("dev_mode_config").getBoolean("show_internal_log", false)) return@on
                mAdapter.pushLog(log)
                recycler.post {
                    recycler.smoothScrollToPosition(mAdapter.data.size - 1)
                }
            }
        }
        onDismiss {
            job.cancel()
        }
    }
     */

    /*
    InfoSheet().show(this) {
        title("Do you want to install Awake?")
        peekHeight(dp(400))
        content("Awake is a beautiful alarm app with morning challenges, advanced alarm management and more.")
        onNegative("No") {
            // Handle event
        }
        onPositive("Install") {
            // Handle event
        }
    }

    ColorSheet().show(this, width = resources.getDimensionPixelSize(R.dimen.dialog_width)) {
        //peekHeight(480)
        peekHeight(dp(400))
        title("Background color")
        onPositive { color ->
            // Use color
        }
        displayHandle(true)
    }
     */
    CustomSheet().show(this) {

    }
}

@SuppressLint("CheckResult")
fun Context.showErrorLogDialog(title: String, e: Throwable) {
    MaterialDialog(this, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
        cornerRadius(25f)
        cancelOnTouchOutside(false)
        noAutoDismiss()
        title(text = title)
        message(text = e.toString() + "\n" + e.stackTraceToString())
        positiveButton(text = "닫기", click = MaterialDialog::dismiss)
    }
}