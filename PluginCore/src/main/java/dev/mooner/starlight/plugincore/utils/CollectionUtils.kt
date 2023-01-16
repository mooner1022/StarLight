/*
 * CollectionUtils.kt created by Minki Moon(mooner1022) on 1/14/23, 12:41 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.utils

inline fun <reified T> Array<out T>.joinClassNames(): String =
    this.joinToString { cl ->
        cl?.let { it::class.simpleName.toString() }
            ?: "null"
    }