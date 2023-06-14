/*
 * TLoggerImpl.kt created by Minki Moon(mooner1022) on 22. 12. 25. 오전 11:48
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.logger.internal

import dev.mooner.starlight.plugincore.config.Flags
import dev.mooner.starlight.plugincore.logger.LazyEval
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.logger.TLogger

class TLoggerImpl(
    private val tag: String
): TLogger {

    override fun verbose(msg: LazyEval) =
        log(Logger::v, msg)

    override fun debug(msg: LazyEval) =
        log(Logger::d, msg)

    override fun info(msg: LazyEval) =
        log(Logger::i, msg)

    override fun info(flags: Flags, msg: LazyEval) =
        Logger.i(tag, evaluate(msg), flags)

    override fun warn(msg: LazyEval) =
        log(Logger::w, msg)

    override fun <T : Throwable> warn(throwable: T) {
        Logger.e(tag, throwable)
    }

    override fun error(msg: LazyEval) =
        log(Logger::e, msg)

    override fun <T : Throwable> error(throwable: T) {
        Logger.e(tag, throwable)
    }

    override fun wtf(msg: LazyEval) =
        log(Logger::wtf, msg)

    override fun log(type: LogType, msg: LazyEval) =
        Logger.log(type, tag, evaluate(msg))

    private fun log(func: (String, String) -> Unit, msg: LazyEval) =
        func(tag, evaluate(msg))

    private fun evaluate(block: LazyEval): String =
        when (val result = block()) {
            is String -> result
            null -> "null"
            else -> result.toString()
        }
}