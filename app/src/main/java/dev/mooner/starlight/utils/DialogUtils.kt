package dev.mooner.starlight.utils

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.UiContext
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.callbacks.onShow
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.DialogLogsBinding
import dev.mooner.starlight.logging.LogCollector
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

typealias ConfirmCallback = (confirm: Boolean) -> Unit

@UiContext
fun Context.showLogsDialog() {
    val logUpdateScope = CoroutineScope(Dispatchers.Default)

    MaterialDialog(this, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
        setCommonAttrs()
        cancelOnTouchOutside(true)
        title(res = R.string.title_logs)

        val activity = this@showLogsDialog

        val binding = DialogLogsBinding.inflate(LayoutInflater.from(activity))
        customView(view = binding.root)

        onShow { _ ->
            val showInternalLog = GlobalConfig
                .category("dev_mode_config")
                .getBoolean("show_internal_log", false)
            val logs = LogCollector.logs
            val mAdapter = LogsRecyclerViewAdapter().withData(logs)
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

            EventHandler.on<Events.Log.Create>(logUpdateScope) {
                if (log.type == LogType.VERBOSE && !showInternalLog) return@on
                binding.rvLog.post {
                    mAdapter.push(log)
                    binding.rvLog.smoothScrollToPosition(mAdapter.getItems().size - 1)
                }
            }
        }

        onDismiss {
            logUpdateScope.cancel()
        }

        negativeButton(res = R.string.close) {
            dismiss()
        }
    }
}

fun showConfirmDialog(context: Context, title: String, message: String, onDismiss: ConfirmCallback? = null) {
    MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).noAutoDismiss().show {
        setCommonAttrs()
        cancelOnTouchOutside(false)
        title(text = title)
        message(text = message)
        positiveButton(res = R.string.ok) {
            onDismiss?.invoke(true)
            dismiss()
        }
        negativeButton(res = R.string.cancel) {
            onDismiss?.invoke(false)
            dismiss()
        }
    }
}

fun Context.showErrorLogDialog(title: String, e: Throwable) {
    MaterialDialog(this, BottomSheet(LayoutMode.WRAP_CONTENT)).noAutoDismiss().show {
        setCommonAttrs()
        cancelOnTouchOutside(false)
        title(text = title)
        message(text = e.toString() + "\n" + e.stackTraceToString())
        positiveButton(res = R.string.close, click = MaterialDialog::dismiss)
    }
}

fun MaterialDialog.setCommonAttrs() {
    cornerRadius(res = R.dimen.card_radius)
}

context(LifecycleOwner)
fun MaterialDialog.setCommonAttrs() {
    cornerRadius(res = R.dimen.card_radius)
    lifecycleOwner(this@LifecycleOwner)
}