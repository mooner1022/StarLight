package dev.mooner.starlight.ui.config.list

import android.content.Context
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
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.config.ConfigObject
import dev.mooner.starlight.plugincore.config.OnDrawBlock
import dev.mooner.starlight.plugincore.config.OnInflateBlock
import dev.mooner.starlight.plugincore.config.data.PrimitiveTypedString
import dev.mooner.starlight.ui.config.ConfigAdapter
import java.util.*

class ListRecyclerAdapter(
    val context: Context,
    val onDrawBlock: OnDrawBlock,
    val onInflateBlock: OnInflateBlock,
    val structure: List<ConfigObject>,
    val data: MutableList<Map<String, Any>> = mutableListOf(),
    val onValueChangedListener: (data: MutableList<Map<String, Any>>) -> Unit
): RecyclerView.Adapter<ListRecyclerAdapter.ListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListRecyclerAdapter.ListViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.config_custom, parent, false)
        onInflateBlock(view)
        return ListViewHolder(view)
    }

    override fun getItemCount(): Int =
        data.size

    override fun getItemViewType(position: Int): Int = 0

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        onDrawBlock(holder.container, data[position])
        holder.container.setOnClickListener {
            MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT))
                .show {
                    title(R.string.edit)

                    //title(res = R.string.add)
                    cornerRadius(res = R.dimen.card_radius)
                    if (context is LifecycleOwner)
                        lifecycleOwner(context as LifecycleOwner)
                    customView(R.layout.dialog_logs)

                    val recycler: RecyclerView = findViewById(R.id.rvLog)

                    val configAdapter = ConfigAdapter.Builder(view.context) {
                        bind(recycler)
                        savedData(mapOf("list_structure" to data[position].mapValues { PrimitiveTypedString.from(it.value) }))
                        onConfigChanged { _, id, _, d ->
                            val cpyMap = data[position].toMutableMap()
                            cpyMap[id] = d
                            data[position] = cpyMap
                        }
                        buildStruct {
                            category {
                                id = "list_structure"
                                items = structure
                            }
                        }
                    }.build()

                    onDismiss {
                        configAdapter.destroy()
                    }

                    positiveButton(R.string.ok) {
                        notifyItemChanged(position)
                        onValueChangedListener(data)
                    }
                    negativeButton(R.string.cancel)
                }
        }
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