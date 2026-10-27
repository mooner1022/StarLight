/*
 * ParentConfigAdapter.kt created by Minki Moon(mooner1022) on 5/3/24, 5:48 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.configdsl.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

class ParentConfigAdapter(
    private var configStructure  : ConfigStructure,
    private val configData       : MutableDataMap,
    coroutineContext : CoroutineContext,
): RecyclerView.Adapter<BaseViewHolder>() {

    private val eventScope = CoroutineScope(coroutineContext + SupervisorJob())

    private val viewTypes     : MutableMap<KClass<*>, Int> = hashMapOf()
    private val viewInstances : MutableList<RootConfigOption<*, *>> = arrayListOf()

    private val eventPublisher = MutableSharedFlow<ConfigOption.EventData>(
        extraBufferCapacity = Channel.UNLIMITED
    )
    val eventFlow
        get() = eventPublisher.asSharedFlow()

    val isHavingError: Boolean
        get() = configStructure.any(RootConfigOption<*, *>::hasError)

    override fun getItemViewType(position: Int): Int =
        viewTypes.getValue(configStructure[position]::class)

    override fun getItemCount(): Int =
        configStructure.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder =
        viewInstances[viewType].onCreateViewHolder(parent)

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        val option = configStructure[position]
        val childData = configData[option.id] ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        (option as RootConfigOption<BaseViewHolder, Any>).onDraw(holder, childData)
    }

    fun updateStruct(structure: ConfigStructure) {
        configStructure = structure
        register(structure)
    }

    fun updateData(data: MutableDataMap) {
        configData.clear()
        data.forEach(configData::put)
    }

    fun notifyAllItemInserted() {
        notifyItemRangeInserted(0, configStructure.size)
    }

    fun destroy() {
        eventScope.cancel()

        configStructure.forEach(RootConfigOption<*, *>::onDestroyed)
    }

    private fun register(structure: ConfigStructure) {
        for (option in structure) {
            viewTypes.getOrPut(option::class) {
                viewInstances += option
                viewInstances.lastIndex
            }
            option.init(eventPublisher)
        }
    }

    init {
        register(configStructure)

        eventFlow
            .filterIsInstance<ConfigOption.RootUpdateData>()
            .onEach { data ->
                configData[data.rootId]?.also { ent ->
                    ent[data.provider] = data.jsonData
                }
            }
            .launchIn(eventScope)
    }
}
