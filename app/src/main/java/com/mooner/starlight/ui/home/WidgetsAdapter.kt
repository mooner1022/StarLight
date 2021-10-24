package com.mooner.starlight.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.widget.IWidget
import com.mooner.starlight.plugincore.widget.WidgetSize

class WidgetsAdapter (
    private val context: Context
): RecyclerView.Adapter<WidgetsAdapter.ViewHolder>() {

    var data: List<IWidget> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when(viewType) {
            WidgetSize.Slim.viewType   -> R.layout.card_widget_slim
            WidgetSize.Medium.viewType -> R.layout.card_widget_medium
            WidgetSize.Large.viewType  -> R.layout.card_widget_large
            WidgetSize.XLarge.viewType   -> R.layout.card_widget_xlarge
            else                       -> R.layout.card_widget_medium
        }
        val view = LayoutInflater.from(context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun getItemViewType(position: Int): Int {
        return data[position].size.viewType
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val viewData = data[position]
        viewData.onCreateWidget(holder.view)
    }

    fun onResume() {
        data.forEach { it.onResumeWidget() }
    }

    fun onPause() {
        data.forEach { it.onPauseWidget() }
    }

    fun onDestroy() {
        data.forEach { it.onDestroyWidget() }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var view: View

        init {
            view = itemView.findViewById(R.id.view)
        }
    }
}