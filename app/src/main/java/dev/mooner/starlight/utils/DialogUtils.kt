package dev.mooner.starlight.utils

import android.annotation.SuppressLint
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun Context.showLogsDialog(): MaterialDialog {
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