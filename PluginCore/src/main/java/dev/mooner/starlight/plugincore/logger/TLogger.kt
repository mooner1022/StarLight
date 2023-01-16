/*
 * TLogger.kt created by Minki Moon(mooner1022) on 22. 12. 25. 오전 11:28
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.logger

typealias LazyEval = () -> Any?

interface TLogger {

    fun verbose(msg: LazyEval)

    fun debug(msg: LazyEval)

    fun info(msg: LazyEval)

    fun warn(msg: LazyEval)

    fun error(msg: LazyEval)

    fun <T> warn(throwable: T) where T: Throwable

    fun <T> error(throwable: T) where T: Throwable

    fun wtf(msg: LazyEval)
}