/*
 * FlagUtils.kt created by Minki Moon(mooner1022) on 22. 1. 2. 오후 9:36
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package com.mooner.starlight.plugincore.utils

fun flagOf(vararg flags: Int): Int {
    val result = 0x0
    for (flag in flags)
        result or flag
    return result
}

infix fun Int.addFlag(flag: Int): Int = this or flag

infix fun Int.hasFlag(flag: Int): Boolean = (this and flag) != 0