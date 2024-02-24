/*
 * ConfigAdapter.kt created by Minki Moon(mooner1022) on 2/17/24, 4:16 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.adapters

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.configdsl.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonElement
import kotlin.properties.Delegates

typealias OnValueUpdatedListener = suspend (parentId: String, id: String, value: Any, jsonValue: JsonElement) -> Unit
typealias StructBlock = () -> ConfigStructure

class ConfigAdapter private constructor(
    private var structBlock : StructBlock,
    private val configData  : MutableDataMap,
): DefaultLifecycleObserver {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var parentAdapter: ParentConfigAdapter<*>? =
        ConfigDSL.createParentAdapter(
            configStructure  = structBlock(),
            configData       = configData,
            coroutineContext = coroutineScope.coroutineContext
        )

    val isDestroyed get() = parentAdapter == null

    val hasError get() = parentAdapter?.isHavingError == true

    fun onEvent(callback: suspend (data: ConfigOption.EventData) -> Unit) {
        checkDestroyed()
        parentAdapter
            ?.eventFlow
            ?.onEach(callback)
            ?.launchIn(coroutineScope)
    }

    fun updateData(data: MutableDataMap) {
        checkDestroyed()
        parentAdapter!!.updateData(data)
    }

    fun redraw() {
        checkDestroyed()
        parentAdapter?.notifyItemRangeChanged(0, configData.size)
    }

    fun destroy() {
        coroutineScope.cancel()
        parentAdapter?.destroy()
        parentAdapter = null
    }

    override fun onDestroy(owner: LifecycleOwner) {
        destroy()
        super.onDestroy(owner)
    }

    private fun checkDestroyed() =
        require(!isDestroyed) { "ConfigAdapter already destroyed" }

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

        private var structBlock: StructBlock by Delegates.notNull()
        private var configData : MutableDataMap = hashMapOf()

        private var listener: OnValueUpdatedListener = { _, _, _, _ -> }

        fun bind(recyclerView: RecyclerView) {
            this.recyclerView = recyclerView
        }

        fun lifecycleOwner(owner: LifecycleOwner) {
            this.lifecycleOwner = owner
        }

        fun structure(data: () -> ConfigStructure) {
            this.structBlock = data
        }

        fun buildStruct(block: ConfigBuilder.() -> Unit) {
            val builder = ConfigBuilder().apply(block)
            structure { builder.build(flush = true) }
        }

        fun configData(data: MutableDataMap) {
            configData = data
        }

        fun onValueUpdated(listener: OnValueUpdatedListener) {
            this.listener = listener
        }

        fun build(): ConfigAdapter {
            val configAdapter = ConfigAdapter(structBlock, configData).apply {
                if (lifecycleOwner != null)
                    lifecycleOwner!!.lifecycle.addObserver(this)
                onEvent { data ->
                    val (parentId, id) = data.provider.split(":")
                    listener(parentId, id, data.data, data.jsonData)
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