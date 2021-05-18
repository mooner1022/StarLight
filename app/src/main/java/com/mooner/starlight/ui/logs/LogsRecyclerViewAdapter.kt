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

class LogsRecyclerViewAdapter(
    private val context: Context,
): RecyclerView.Adapter<LogsRecyclerViewAdapter.LogsViewHolder>() {
    var data = mutableListOf<LogData>()
    var saved: MutableMap<String, TypedString> = mutableMapOf()

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
        holder.stateColor.setCardBackgroundColor(color)
        holder.title.text = viewData.tag
        holder.content.text = viewData.message
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    inner class LogsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val stateColor: CardView = itemView.findViewById(R.id.logInfoColor)
        val title: TextView = itemView.findViewById(R.id.logTitleText)
        val content: TextView = itemView.findViewById(R.id.logContentText)
    }
}