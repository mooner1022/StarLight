package com.mooner.starlight.ui.config

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.config.*
import com.mooner.starlight.plugincore.models.TypedString

class ParentAdapter(
    private val context: Context,
    private val onConfigChanged: (parentId: String, id: String, view: View, data: Any) -> Unit
): RecyclerView.Adapter<ParentAdapter.ViewHolder>() {
    var data: List<CategoryConfigObject> = mutableListOf()
    var saved: MutableMap<String, MutableMap<String, TypedString>> = hashMapOf()
    var isHavingError = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.config_category, parent, false)
        return ViewHolder(view, viewType)
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int {
        return ConfigObjectType.CATEGORY.viewType
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val viewData = data[position]

        holder.categoryTitle.text = viewData.title
        holder.categoryTitle.apply {
            text = viewData.title
            setTextColor(viewData.textColor)
        }

        val children = viewData.items
        val recyclerAdapter = ConfigAdapter(context) { id, view, data ->
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
    inner class ViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        var categoryTitle: TextView
        var categoryItems: RecyclerView

        init {
            categoryTitle = itemView.findViewById(R.id.textView_configTitle)
            categoryItems = itemView.findViewById(R.id.recyclerViewCategory)
        }
    }
}