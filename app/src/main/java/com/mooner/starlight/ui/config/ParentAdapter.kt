package com.mooner.starlight.ui.config

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.config.CategoryConfigObject
import com.mooner.starlight.plugincore.config.ConfigObjectType
import com.mooner.starlight.plugincore.models.TypedString

class ParentAdapter(
    private val context: Context,
    private val onConfigChanged: (parentId: String, id: String, view: View, data: Any) -> Unit
): RecyclerView.Adapter<ParentAdapter.ViewHolder>() {

    var data: List<CategoryConfigObject> = mutableListOf()
    var saved: Map<String, Map<String, TypedString>> = mutableMapOf()
    private var recyclerAdapter: ConfigAdapter? = null
    var isHavingError = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.config_category, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int {
        return ConfigObjectType.CATEGORY.viewType
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val viewData = data[position]

        if (viewData.title.isNotBlank()) {
            holder.categoryTitle.apply {
                visibility = View.VISIBLE
                text = viewData.title
                setTextColor(viewData.textColor)
            }
        } else {
            holder.categoryTitle.visibility = View.INVISIBLE
        }


        val children = viewData.items
        recyclerAdapter = ConfigAdapter(context) { id, view, data ->
            onConfigChanged(viewData.id, id, view, data)
        }.apply {
            this.data = children
            saved = this@ParentAdapter.saved[viewData.id]?.toMutableMap()?: mutableMapOf()
            notifyDataSetChanged()
        }
        val mLayoutManager = LinearLayoutManager(context)
        holder.categoryItems.apply {
            adapter = recyclerAdapter
            layoutManager = mLayoutManager
        }
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val categoryTitle: TextView     = itemView.findViewById(R.id.title)
        val categoryItems: RecyclerView = itemView.findViewById(R.id.recyclerViewCategory)
    }

    fun destroy() {
        recyclerAdapter = null
    }
}