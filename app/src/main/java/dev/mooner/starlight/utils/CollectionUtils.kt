/*
 * CollectionUtils.kt created by Minki Moon(mooner1022) on 1/24/23, 9:19 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.utils

fun <T> List<T>.dropBefore(size: Int): List<T> {
    return if (this.size <= size)
        this
    else
        this.subList(this.size - size, this.size)
}