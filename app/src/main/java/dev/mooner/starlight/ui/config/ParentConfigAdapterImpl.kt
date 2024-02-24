/*
 * ParentConfigAdapterImpl.kt created by Minki Moon(mooner1022) on 2/17/24, 6:08 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.config

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import dev.mooner.configdsl.*
import dev.mooner.configdsl.adapters.ParentConfigAdapter
import dev.mooner.configdsl.options.CategoryConfigOption
import dev.mooner.starlight.utils.isDevMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

private typealias HashCode = Int

class ParentConfigAdapterImpl(
    configStructure  : ConfigStructure,
    configData       : MutableDataMap,
    coroutineContext : CoroutineContext,
): ParentConfigAdapter<BaseViewHolder>(configStructure, configData, coroutineContext) {

    private var descCache: MutableMap<String, String> = hashMapOf()
    private val eventScope = CoroutineScope(coroutineContext + SupervisorJob())

    private var childAdapters : MutableList<CategoryRecyclerAdapter> = arrayListOf()
    private val viewTypes     : MutableList<HashCode> = arrayListOf()
    private val viewInstances : MutableList<RootConfigOption<*, *>> = arrayListOf()

    private val eventPublisher = MutableSharedFlow<ConfigOption.EventData>(
        extraBufferCapacity = Channel.UNLIMITED)
    override val eventFlow
        get() = eventPublisher.asSharedFlow()

    override val isHavingError: Boolean
        get() = childAdapters.any(CategoryRecyclerAdapter::hasError)

    override fun getItemViewType(position: Int): Int {
        val viewData = configStructure[position]

        return viewTypes.indexOf(viewData::class.hashCode())
    }

    override fun getItemCount(): Int =
        if (isDevMode)
            configStructure.size
        else
            configStructure.count { it is CategoryConfigOption && !it.isDevModeOnly }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val instance = viewInstances[viewType]
        return instance.onCreateViewHolder(parent)
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        val posData = configStructure[position]
        val viewData = if (posData is CategoryConfigOption && posData.isDevModeOnly) {
            if (isDevMode) posData
            else configStructure[position + 1]
        } else posData

        val childData = configData[viewData.id] ?: emptyMap()
        if (viewData is CategoryConfigOption) {
            val childOptions = viewData.items
            val childAdapter = CategoryRecyclerAdapter(
                options    = childOptions,
                optionData = childData,
                eventScope = eventScope,
            )
            val mLayoutManager = LinearLayoutManager(holder.itemView.context)
            (holder as CategoryConfigOption.CategoryViewHolder).itemList.apply {
                adapter = childAdapter
                layoutManager = mLayoutManager
            }
            childAdapter.eventFlow
                .filterNot { it.provider.startsWith("dep:") }
                .onEach {
                    configData.computeIfAbsent(viewData.id) { hashMapOf() }[it.provider] = it.jsonData
                    eventPublisher.emit(ConfigOption.EventData(
                        viewData.id + ":" + it.provider,
                        it.data,
                        it.jsonData
                    ))
                }
                .launchIn(eventScope)
            childAdapter.notifyItemRangeInserted(0, childOptions.size)

            viewData.title.nullIfBlank()?.let {
                holder.title.apply {
                    text = it
                    visibility = View.VISIBLE
                    setTextColor(viewData.textColor ?: context.getColor(R.color.text))
                }
            } ?: let {
                holder.title.visibility = View.INVISIBLE
            }
        } else {
            @Suppress("UNCHECKED_CAST")
            viewData as RootConfigOption<BaseViewHolder, Any>
            println("childData ~~~~~")
            for ((key, value) in childData)
                println("\t $key: $value")
            println("~~~~~~~~~~~~~~~~~~~~")
            println("viewData.onDraw: ${viewData.id}")
            viewData.onDraw(holder, childData)
        }
    }

    override fun updateStruct(structure: ConfigStructure) {
        configStructure = structure
    }

    override fun updateData(data: MutableDataMap) {
        configData.clear()
        data.forEach(configData::put)
    }

    fun notifyAllItemInserted() {
        notifyItemRangeInserted(0, configStructure.size)
    }

    override fun destroy() {
        eventScope.cancel()

        childAdapters.forEach(CategoryRecyclerAdapter::destroy)
        childAdapters.clear()
    }

    private fun String.nullIfBlank(): String? =
        this.ifBlank { null }

    init {
        for (option in configStructure) {
            val hash = option::class.hashCode()
            if (hash !in viewTypes) {
                viewTypes     += hash
                viewInstances += option
            }
            option.init(eventPublisher)
        }
    }
}