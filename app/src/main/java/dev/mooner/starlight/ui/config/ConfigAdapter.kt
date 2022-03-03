package dev.mooner.starlight.ui.config

import android.content.Context
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.starlight.plugincore.config.CategoryConfigObject
import dev.mooner.starlight.plugincore.config.TypedString
import dev.mooner.starlight.plugincore.logger.Logger
import kotlin.properties.Delegates

typealias OnConfigChangedListener = (parentId: String, id: String, view: View?, data: Any) -> Unit
typealias DataBlock = () -> List<CategoryConfigObject>

class ConfigAdapter private constructor(
    private var dataBlock: DataBlock
): DefaultLifecycleObserver {

    private var parentAdapter: ParentRecyclerAdapter? = null

    private fun checkDestroyed() = assert(!isDestroyed) { "ConfigAdapter already destroyed" }

    val isDestroyed get() = parentAdapter == null

    fun reload() {
        checkDestroyed()
        parentAdapter!!.apply {
            val orgDataSize = configs.size
            notifyItemRangeRemoved(0, orgDataSize)
            configs = dataBlock()
            notifyAllItemInserted()
        }
    }

    fun reload(replaceWith: DataBlock) {
        this.dataBlock = replaceWith
        reload()
    }

    val hasError get() = parentAdapter?.isHavingError == true

    override fun onDestroy(owner: LifecycleOwner) {
        destroy()
        super.onDestroy(owner)
    }

    fun destroy() {
        Logger.v("onDestroy() called")
        parentAdapter?.destroy()
        parentAdapter = null
    }

    class Builder {

        private val context: Context

        constructor(context: Context, block: Builder.() -> Unit) {
            this.context = context
            this.apply(block)
        }

        constructor(context: Context) {
            this.context = context
        }

        private var recyclerView: RecyclerView by Delegates.notNull()
        private var lifecycleOwner: LifecycleOwner? = null

        private var dataBlock: DataBlock by Delegates.notNull()
        private var savedData: Map<String, Map<String, TypedString>> = emptyMap()

        private var listener: OnConfigChangedListener = { _, _, _, _ -> }

        fun bind(recyclerView: RecyclerView) {
            this.recyclerView = recyclerView
        }

        fun lifecycleOwner(owner: LifecycleOwner) {
            this.lifecycleOwner = owner
        }

        fun configs(data: () -> List<CategoryConfigObject>) {
            this.dataBlock = data
        }

        fun savedData(data: Map<String, Map<String, TypedString>>) {
            savedData = data
        }

        fun onConfigChanged(listener: OnConfigChangedListener) {
            this.listener = listener
        }

        fun build(): ConfigAdapter {
            val configAdapter = ConfigAdapter(dataBlock).apply {
                if (lifecycleOwner != null)
                    lifecycleOwner!!.lifecycle.addObserver(this)

                parentAdapter = ParentRecyclerAdapter(context, dataBlock(), savedData, listener).apply {
                    notifyAllItemInserted()
                }
            }

            val mLayoutManager = LinearLayoutManager(context)
            recyclerView.apply {
                layoutManager = mLayoutManager
                adapter = configAdapter.parentAdapter
            }
            return configAdapter
        }
    }
}