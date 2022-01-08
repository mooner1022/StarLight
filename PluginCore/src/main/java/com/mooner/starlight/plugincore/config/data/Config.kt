/*
 * Config.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package com.mooner.starlight.plugincore.config.data

interface Config {
    operator fun get(id: String): ConfigCategory

    fun getCategory(id: String): ConfigCategory

    fun getCategoryOrNull(id: String): ConfigCategory?
}