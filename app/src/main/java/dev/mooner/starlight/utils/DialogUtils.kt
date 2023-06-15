package dev.mooner.starlight.utils

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.maxkeppeler.sheets.core.Sheet
import com.maxkeppeler.sheets.core.SheetStyle
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
import kotlinx.coroutines.*

typealias ConfirmCallback = (confirm: Boolean) -> Unit

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

        val logs = LogCollector.logs
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
            EventHandler.on<Events.Log.Create>(this) {
                if (log.type == LogType.VERBOSE && !GlobalConfig.category("dev_mode_config").getBoolean("show_internal_log", false)) return@on
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
    CustomSheet().show(this) {

    }
}

fun showConfirmDialog(context: Context, title: String, message: String, onDismiss: ConfirmCallback? = null) {
    MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
        setCommonAttrs()
        cancelOnTouchOutside(false)
        noAutoDismiss()
        title(text = title)
        message(text = message)
        positiveButton(text = context.getString(R.string.ok)) {
            onDismiss?.invoke(true)
            dismiss()
        }
        negativeButton(text = context.getString(R.string.cancel)) {
            onDismiss?.invoke(false)
            dismiss()
        }
    }
}

@SuppressLint("CheckResult")
fun Context.showErrorLogDialog(title: String, e: Throwable) {
    MaterialDialog(this, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
        setCommonAttrs()
        cancelOnTouchOutside(false)
        noAutoDismiss()
        title(text = title)
        message(text = e.toString() + "\n" + e.stackTraceToString())
        positiveButton(text = "닫기", click = MaterialDialog::dismiss)
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