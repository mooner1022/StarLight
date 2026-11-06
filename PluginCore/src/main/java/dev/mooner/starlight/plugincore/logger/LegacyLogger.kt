/*
 * Logger.kt created by Minki Moon(mooner1022) on 22. 12. 25. 오후 5:29
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.logger

import dev.mooner.starlight.plugincore.logger.internal.Logger

@Deprecated(
    message = "Created and used for legacy compatibility, do not use it.",
    replaceWith = ReplaceWith(
        expression = "LoggerFactory.logger",
        "dev.mooner.starlight.plugincore.logger.LoggerFactory"
    )
)
class Logger {

    companion object {

        @JvmStatic
        fun v(message: String) =
            v(tag = null, message = message)

        @JvmStatic
        fun v(tag: String?, message: String) = Logger.log(
            type = LogType.VERBOSE,
            tag = tag,
            message = message
        )

        @JvmStatic
        fun d(message: String) =
            d(tag = null, message = message)

        @JvmStatic
        fun d(tag: String?, message: String) = Logger.log(
            type = LogType.DEBUG,
            tag = tag,
            message = message
        )

        @JvmStatic
        fun i(message: String) =
            i(tag = null, message = message)

        @JvmStatic
        fun i(tag: String?, message: String) = Logger.log(
            type = LogType.INFO,
            tag = tag,
            message = message
        )

        @JvmStatic
        fun w(message: String) =
            w(tag = null, message = message)

        @JvmStatic
        fun w(tag: String?, message: String) = Logger.log(
            type = LogType.WARN,
            tag = tag,
            message = message
        )

        @JvmStatic
        fun e(tag: String, throwable: Throwable) =
            e(tag, throwable.message ?: "null")

        @JvmStatic
        fun e(throwable: Throwable) =
            e(throwable.message ?: "null")

        @JvmStatic
        fun e(message: String) =
            e(tag = null, message = message)

        @JvmStatic
        fun e(tag: String?, message: String) = Logger.log(
            type = LogType.ERROR,
            tag = tag,
            message = message
        )

        // What a Terrible Failure!
        @JvmStatic
        fun wtf(message: String) =
            wtf(tag = null, message = message)

        @JvmStatic
        fun wtf(tag: String?, message: String) = Logger.log(
            type = LogType.CRITICAL,
            tag = tag,
            message = message
        )
    }
}