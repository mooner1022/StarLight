package dev.mooner.starlight.ui.widget.config

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.widget.Widget
import dev.mooner.starlight.plugincore.widget.WidgetSize
import java.util.*

class WidgetsThumbnailAdapter (
    private val context: Context,
    private val onEdited: (data: List<Widget>) -> Unit
): RecyclerView.Adapter<WidgetsThumbnailAdapter.ViewHolder>() {

    var data: MutableList<Widget> = mutableListOf()

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
        holder.title.apply {
            visibility = View.VISIBLE
            text = viewData.name
        }
        (holder.view as FrameLayout).removeAllViews()
        viewData.onCreateThumbnail(holder.view)
    }

    fun removeData(position: Int) {
        data.removeAt(position)
        notifyItemRemoved(position)
        onEdited(data)
    }

    fun swapData(fromPos: Int, toPos: Int) {
        Collections.swap(data, fromPos, toPos)
        notifyItemMoved(fromPos, toPos)
        onEdited(data)
    }

    fun notifyAllItemInserted() {
        notifyItemRangeInserted(0, data.size)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val view: View = itemView.findViewById(R.id.view)
        val title: TextView = itemView.findViewById(R.id.title)
    }
}