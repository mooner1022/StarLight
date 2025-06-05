/*
 * LoggingUtils.kt created by Minki Moon(mooner1022) on 22. 12. 25. 오전 2:56
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.utils

import android.content.Context
import androidx.annotation.StringRes
import dev.mooner.starlight.plugincore.logger.TLogger

context(context: Context)
fun TLogger.verbose(@StringRes res: Int) =
    verbose { context.getString(res) }

context(context: Context)
fun TLogger.verbose(@StringRes res: Int, vararg formatArgs: Any) =
    verbose { context.getString(res, formatArgs) }

context(context: Context)
fun TLogger.debug(@StringRes res: Int) =
    debug { context.getString(res) }

context(context: Context)
fun TLogger.debug(@StringRes res: Int, vararg formatArgs: Any) =
    debug { context.getString(res, formatArgs) }

context(context: Context)
fun TLogger.info(@StringRes res: Int) =
    info { context.getString(res) }

context(context: Context)
fun TLogger.info(@StringRes res: Int, vararg formatArgs: Any) =
    info { context.getString(res, formatArgs) }

context(context: Context)
fun TLogger.warn(@StringRes res: Int) =
    warn { context.getString(res) }

context(context: Context)
fun TLogger.warn(@StringRes res: Int, vararg formatArgs: Any) =
    warn { context.getString(res, formatArgs) }

context(context: Context)
fun TLogger.error(@StringRes res: Int) =
    warn { context.getString(res) }

context(context: Context)
fun TLogger.error(@StringRes res: Int, vararg formatArgs: Any) =
    warn { context.getString(res, formatArgs) }