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
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
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
            MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT))
                .show {
                    title(R.string.edit)
                    cornerRadius(res = R.dimen.card_radius)
                    if (context is LifecycleOwner)
                        lifecycleOwner(context as LifecycleOwner)
                    customView(R.layout.dialog_logs)

                    val recycler: RecyclerView = findViewById(R.id.rvLog)

                    val cloned = data[position].toMutableMap()
                    val configAdapter = ConfigAdapter.Builder(view.context) {
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

                    onDismiss {
                        configAdapter.destroy()
                    }

                    positiveButton(R.string.ok) {
                        data[position] = JsonObject(cloned)
                        notifyItemChanged(position)
                        onValueChangedListener(data)
                    }
                    negativeButton(R.string.cancel)
                }
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