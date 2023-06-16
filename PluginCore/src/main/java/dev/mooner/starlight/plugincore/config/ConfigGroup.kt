/*
 * ConfigStruct.kt created by Minki Moon(mooner1022) on 4/23/23, 11:38 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config

public typealias Flags = Int

class ConfigGroup(
    val flags: Flags
) {

    private val cells: MutableSet<ConfigCell<*>> = hashSetOf()

    //fun publishEvent()
}