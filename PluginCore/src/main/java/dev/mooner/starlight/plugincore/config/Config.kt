/*
 * Config.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config

import dev.mooner.starlight.plugincore.config.category.ConfigCategory

public typealias ConfigData = Map<String, Map<String, TypedString>>

interface Config {

    fun getData(): ConfigData

    operator fun get(id: String): ConfigCategory

    operator fun contains(id: String): Boolean

    fun category(id: String): ConfigCategory

    fun categoryOrNull(id: String): ConfigCategory?
}