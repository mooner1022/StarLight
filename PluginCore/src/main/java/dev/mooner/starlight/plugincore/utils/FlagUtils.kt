/*
 * FlagUtils.kt created by Minki Moon(mooner1022) on 22. 1. 2. 오후 9:36
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.utils

typealias BitFlag = Int
typealias Flags   = Int

fun flagOf(vararg flags: Flags): Flags {
    val result = 0x0
    for (flag in flags)
        result or flag
    return result
}

infix fun Flags.addFlag(flag: BitFlag): Flags = this or flag

infix fun Flags.hasFlag(flag: BitFlag): Boolean = (this and flag) != 0