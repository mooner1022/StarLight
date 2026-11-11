/*
 * PluginCalls.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.utils

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block], which calls code the app didn't write: a plugin's listener, API, widget or language. Whatever it
 * throws is handed to [onFailure] and null is returned, so one plugin that fails takes neither the app down nor the
 * turn of the plugins after it.
 *
 * A [LinkageError] is caught beside exceptions: it is what a plugin built against another version of the app, or
 * one whose native library didn't load, throws. Other errors, as running out of memory, aren't a plugin's to cause
 * and go on.
 */
internal inline fun <T> callPlugin(onFailure: (Throwable) -> Unit, block: () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        // Not a failure: the coroutine the call was made from was cancelled.
        throw e
    } catch (e: Exception) {
        onFailure(e)
        null
    } catch (e: LinkageError) {
        onFailure(e)
        null
    }
