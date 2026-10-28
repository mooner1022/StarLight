/*
 * ListRecyclerAdapter.kt created by Minki Moon(mooner1022) on 2/17/24, 4:11 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.configdsl.ConfigDSL
import dev.mooner.configdsl.ConfigOption
import dev.mooner.configdsl.R
import dev.mooner.configdsl.options.OnDrawBlock
import dev.mooner.configdsl.options.OnInflateBlock
import kotlinx.serialization.json.JsonObject
import java.util.*

class ListRecyclerAdapter(
    val onDrawBlock    : OnDrawBlock,
    val onInflateBlock : OnInflateBlock,
    val structure      : List<ConfigOption<*, *>>,
    val data           : MutableList<JsonObject>,
    val onValueChangedListener: (data: MutableList<JsonObject>) -> Unit
): RecyclerView.Adapter<ListRecyclerAdapter.ListViewHolder>() {

    private var isEnabled: Boolean = true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListRecyclerAdapter.ListViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_custom, parent, false)
        onInflateBlock(view)
        return ListViewHolder(view)
    }

    override fun getItemCount(): Int =
        data.size

    override fun getItemViewType(position: Int): Int = 0

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        val context = holder.itemView.context

        onDrawBlock(holder.container, data[position])
        holder.container.setOnClickListener {
            if (!isEnabled)
                return@setOnClickListener
            val recycler = RecyclerView(context).apply {
                overScrollMode = View.OVER_SCROLL_NEVER
                isNestedScrollingEnabled = false
            }
            val cloned = data[position].toMutableMap()
            val configAdapter = ConfigAdapter.Builder(context) {
                bind(recycler)
                configData(mutableMapOf("list_structure" to cloned))
                buildStruct {
                    category {
                        id = "list_structure"
                        items = structure
                    }
                }
                onValueUpdated { _, id, _, jsonData ->
                    cloned[id] = jsonData
                }
            }.build()

            ConfigDSL.dialogProvider.form(
                context = context,
                title = context.getString(R.string.edit),
                content = recycler,
                onConfirm = {
                    data[position] = JsonObject(cloned)
                    notifyItemChanged(position)
                    onValueChangedListener(data)
                },
                onDismiss = configAdapter::destroy,
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun removeData(position: Int) {
        data.removeAt(position)
        notifyItemRemoved(position)
        onValueChangedListener(data)
    }

    fun swapData(fromPos: Int, toPos: Int) {
        Collections.swap(data, fromPos, toPos)
        notifyItemMoved(fromPos, toPos)
        onValueChangedListener(data)
    }

    inner class ListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        var container: FrameLayout = itemView.findViewById(R.id.container)
    }
}