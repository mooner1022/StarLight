package dev.mooner.starlight.ui.widget.config

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.widget.Widget
import dev.mooner.starlight.plugincore.widget.WidgetSize

class WidgetsAdapter (
    private val context: Context
): RecyclerView.Adapter<WidgetsAdapter.ViewHolder>() {

    var data: List<Widget> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when(viewType) {
            WidgetSize.Slim.viewType   -> R.layout.card_widget_slim
            WidgetSize.Medium.viewType -> R.layout.card_widget_medium
            WidgetSize.Large.viewType  -> R.layout.card_widget_large
            WidgetSize.XLarge.viewType -> R.layout.card_widget_xlarge
            WidgetSize.Wrap.viewType   -> R.layout.card_widget_wrap
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
        holder.title.visibility = View.GONE
        holder.view.removeAllViews()
        viewData.onCreateWidget(holder.view)
    }

    fun onResume(lifecycleOwner: LifecycleOwner) {
        for (widget in data) {
            widget.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_RESUME)
            widget.onResumeWidget()
        }
    }

    fun onPause(lifecycleOwner: LifecycleOwner) {
        for (widget in data) {
            widget.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_PAUSE)
            widget.onPauseWidget()
        }
    }

    fun onDestroy(lifecycleOwner: LifecycleOwner) {
        for (widget in data) {
            widget.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_DESTROY)
            widget.onDestroyWidget()
        }
    }

    fun notifyAllItemInserted() {
        notifyItemRangeInserted(0, data.size)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val view: FrameLayout = itemView.findViewById(R.id.view)
        val title: TextView = itemView.findViewById(R.id.title)
    }
}