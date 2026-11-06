/*
 * LoggerFactory.kt created by Minki Moon(mooner1022) on 22. 12. 25. 오전 11:26
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.logger

import dev.mooner.starlight.plugincore.logger.internal.TLoggerImpl

object LoggerFactory {

    fun logger(block: () -> Unit): TLogger =
        logger(getClassName(block))

    fun logger(name: String): TLogger =
        TLoggerImpl(name)

    @Suppress("NOTHING_TO_INLINE")
    private inline fun getClassName(noinline block: () -> Unit) =
        block.javaClass.name
            .split(".")
            .last()
            .let { name ->
                when {
                    name.contains("Kt$") -> name.substringBefore("Kt$")
                    name.contains("$") -> name.substringBefore("$")
                    else -> name
                }
            }
}