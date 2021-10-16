package com.mooner.starlight.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.config.CategoryConfigObject
import com.mooner.starlight.plugincore.config.ConfigObjectType

class WidgetsAdapter (
    private val context: Context,
    private val onConfigChanged: (parentId: String, id: String, view: View, data: Any) -> Unit
): RecyclerView.Adapter<WidgetsAdapter.ViewHolder>() {

    var data: List<CategoryConfigObject> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.config_category, parent, false)
        return ViewHolder(view, viewType)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun getItemViewType(position: Int): Int {
        return ConfigObjectType.CATEGORY.viewType
    }

    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val viewData = data[position]


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