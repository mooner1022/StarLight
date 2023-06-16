/*
 * ConfigData.kt created by Minki Moon(mooner1022) on 4/23/23, 11:26 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config.data

import dev.mooner.starlight.plugincore.config.data.category.ConfigCategory

typealias DataMap = Map<String, Map<String, PrimitiveTypedString>>
typealias MutableDataMap = MutableMap<String, MutableMap<String, PrimitiveTypedString>>

interface ConfigData {

    fun getData(): DataMap

    operator fun get(id: String): ConfigCategory

    operator fun contains(id: String): Boolean

    fun category(id: String): ConfigCategory

    fun categoryOrNull(id: String): ConfigCategory?
}