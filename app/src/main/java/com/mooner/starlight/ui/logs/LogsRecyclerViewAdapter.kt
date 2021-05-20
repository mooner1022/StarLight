package com.mooner.starlight.ui.logs

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.TypedString
import com.mooner.starlight.plugincore.logger.LogData
import com.mooner.starlight.plugincore.logger.LogType
import java.text.SimpleDateFormat

@SuppressLint("SimpleDateFormat")
class LogsRecyclerViewAdapter(
    private val context: Context,
): RecyclerView.Adapter<LogsRecyclerViewAdapter.LogsViewHolder>() {
    var data = mutableListOf<LogData>()
    var saved: MutableMap<String, TypedString> = mutableMapOf()
    private val fullDateFormat = SimpleDateFormat("MM/dd HH:mm")
    private val hourDateFormat = SimpleDateFormat("HH:mm:ss")
    private val dateMillis: Long = 24 * 60 * 60 * 1000

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
            LogType.ERROR -> R.color.code_error
        }
        holder.stateColor.setCardBackgroundColor(context.getColor(color))
        holder.title.text = viewData.tag
        holder.content.text = viewData.message
        holder.timestamp.text = formatDate(viewData.millis)
    }

    fun pushLog(log: LogData, limit: Int = 0) {
        if (limit != 0 && data.size >= limit) {
            data = data.drop(1).toMutableList()
            this.notifyItemRemoved(0)
            data.add(log)
            this.notifyItemInserted(data.size)
        } else {
            data.add(log)
            this.notifyItemInserted(data.size)
        }
    }

    private fun formatDate(millis: Long): String {
        return if (System.currentTimeMillis() / dateMillis != millis / dateMillis) {
            fullDateFormat
        } else {
            hourDateFormat
        }.format(millis)
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    inner class LogsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val stateColor: CardView = itemView.findViewById(R.id.logInfoColor)
        val title: TextView = itemView.findViewById(R.id.logTitleText)
        val content: TextView = itemView.findViewById(R.id.logContentText)
        val timestamp: TextView = itemView.findViewById(R.id.logTimeStampText)
    }
}