/*
 * ParentConfigAdapter.kt created by Minki Moon(mooner1022) on 2/17/24, 6:03 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.adapters

import androidx.recyclerview.widget.RecyclerView
import dev.mooner.configdsl.ConfigOption
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.MutableDataMap
import kotlinx.coroutines.flow.SharedFlow
import kotlin.coroutines.CoroutineContext

abstract class ParentConfigAdapter<VH: RecyclerView.ViewHolder>(
    configStructure  : ConfigStructure,
    val configData   : MutableDataMap,
    coroutineContext : CoroutineContext,
): RecyclerView.Adapter<VH>() {

    var configStructure: ConfigStructure = configStructure
        protected set

    abstract val eventFlow: SharedFlow<ConfigOption.EventData>

    abstract val isHavingError: Boolean

    abstract fun updateStruct(structure: ConfigStructure)

    abstract fun updateData(data: MutableDataMap)

    abstract fun destroy()
}