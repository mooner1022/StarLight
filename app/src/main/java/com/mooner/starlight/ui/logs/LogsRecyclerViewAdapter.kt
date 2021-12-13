package com.mooner.starlight.ui.logs

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.logger.LogData
import com.mooner.starlight.plugincore.logger.LogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("ConstantLocale")
class LogsRecyclerViewAdapter(
    private val context: Context,
): RecyclerView.Adapter<LogsRecyclerViewAdapter.LogsViewHolder>() {

    companion object {
        private val fullDateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        private val hourDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    private val clipboard: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var data: MutableList<LogData> = arrayListOf()
    private val calendar = Calendar.getInstance()
    private val mainScope: CoroutineScope
        get() = CoroutineScope(Dispatchers.Main)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogsViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.card_log, parent, false)
        return LogsViewHolder(view)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: LogsViewHolder, position: Int) {
        val viewData = data[position]

        val color = when(viewData.type) {
            LogType.INFO -> R.color.code_string
            LogType.DEBUG -> R.color.code_purple
            LogType.WARNING -> R.color.code_yellow
            LogType.ERROR -> R.color.code_orange
            LogType.CRITICAL -> R.color.code_error
            LogType.VERBOSE -> R.color.monokai_pro_sky
        }
        holder.stateColor.setCardBackgroundColor(context.getColor(color))
        holder.title.text = viewData.tag?: viewData.type.name
        holder.content.text = viewData.message
        holder.timestamp.text = formatDate(viewData.millis)
        holder.root.setOnLongClickListener {
            val clip = ClipData.newPlainText("로그", viewData.toString())
            clipboard.setPrimaryClip(clip)
            Snackbar.make(it, "로그를 클립보드에 복사했어요.", Snackbar.LENGTH_SHORT).show()
            true
        }
    }

    fun pushLog(log: LogData, limit: Int = 0) {
        mainScope.launch {
            if (limit != 0 && data.size >= limit) {
                data = data.drop(1).toMutableList()
                notifyItemRemoved(0)
                data.add(log)
                notifyItemInserted(data.size)
            } else {
                data.add(log)
                notifyItemInserted(data.size)
            }
        }
    }

    private fun formatDate(millis: Long): String {
        val logCalendar = Calendar.getInstance().apply { timeInMillis = millis }

        return if (calendar.get(Calendar.DAY_OF_YEAR) != logCalendar.get(Calendar.DAY_OF_YEAR)) {
            fullDateFormat
        } else {
            hourDateFormat
        }.format(millis)
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    inner class LogsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: ConstraintLayout = itemView.findViewById(R.id.rootLayout)
        val stateColor: CardView   = itemView.findViewById(R.id.logInfoColor)
        val title: TextView        = itemView.findViewById(R.id.logTitleText)
        val content: TextView      = itemView.findViewById(R.id.logContentText)
        val timestamp: TextView    = itemView.findViewById(R.id.logTimeStampText)
    }
}