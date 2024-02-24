/*
 * ConfigDSL.kt created by Minki Moon(mooner1022) on 2/17/24, 6:01 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl

import dev.mooner.configdsl.adapters.ParentConfigAdapter
import kotlinx.serialization.json.JsonElement
import kotlin.coroutines.CoroutineContext

internal typealias ParentAdapterInstanceBlock = (ConfigStructure, MutableDataMap, CoroutineContext) -> ParentConfigAdapter<*>
typealias DataMap        = Map<String, DataMapEntry>
typealias MutableDataMap = MutableMap<String, MutableDataMapEntry>
typealias DataMapEntry        = Map<String, JsonElement>
typealias MutableDataMapEntry = MutableMap<String, JsonElement>

object ConfigDSL {

    private lateinit var createInstance: ParentAdapterInstanceBlock

    fun registerAdapterImpl(createInstance: ParentAdapterInstanceBlock) {
        this.createInstance = createInstance
    }

    internal fun createParentAdapter(
        configStructure  : ConfigStructure,
        configData       : MutableDataMap,
        coroutineContext : CoroutineContext,
    ): ParentConfigAdapter<*> =
        this.createInstance(configStructure, configData, coroutineContext)
}