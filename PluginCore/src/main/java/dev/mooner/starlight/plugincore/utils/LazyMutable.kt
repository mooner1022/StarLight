/*
 * LazyMutable.kt created by Minki Moon(mooner1022) on 1/6/24, 7:58 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.utils

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class LazyMutable<T>(val initializer: () -> T) : ReadWriteProperty<Any?, T> {
    private object Uninitialized
    private var prop: Any? = Uninitialized

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return if (prop == Uninitialized) {
            synchronized(this) {
               return if (prop == Uninitialized) initializer().also { prop = it } else prop as T
            }
        } else prop as T
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        synchronized(this) {
            prop = value
        }
    }
}

fun <T> lazyMutable(initializer: () -> T): ReadWriteProperty<Any?, T> =
    LazyMutable(initializer)