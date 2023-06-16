/*
 * MutableConfig.kt created by Minki Moon(mooner1022) on 4/23/23, 11:26 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config.data

import dev.mooner.starlight.plugincore.config.data.category.MutableConfigCategory

interface MutableConfig: ConfigData {

    override operator fun get(id: String): MutableConfigCategory

    override fun category(id: String): MutableConfigCategory

    override fun categoryOrNull(id: String): MutableConfigCategory?

    fun push()

    fun edit(block: MutableConfig.() -> Unit)
}