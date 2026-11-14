/*
 * HttpCall.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot

/**
 * A request `Http.fetch` has sent, which a script can cancel while it is under way.
 *
 * ```js
 * const call = Http.fetch("https://example.com/slow", (error, res) => Log.i("http", String(error)));
 * call.cancel(); // the callback gets an IOException: Canceled
 * ```
 */
class HttpCall internal constructor(
    private val handle: CallHandle,
) {

    /**
     * Cancels the request. If it hasn't finished, its callback gets an `IOException` whose message is `Canceled`. It
     * does nothing when the request has finished, and calling it again is harmless.
     */
    fun cancel() =
        handle.cancel()

    /** Whether [cancel] was called, which stays `true` afterwards, also when the request had finished. */
    fun isCanceled(): Boolean =
        handle.canceled
}
