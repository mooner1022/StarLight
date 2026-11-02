/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.api

import dev.mooner.starlight.plugincore.chat.MediaSendResult
import dev.mooner.starlight.plugincore.project.Project
import java.util.concurrent.CompletableFuture

/**
 * The global `MediaSharer` of a project: sends images, videos and other files to KakaoTalk chat rooms.
 *
 * ```js
 * // NeonJS
 * export async function onMessage(msg) {
 *     if (msg.message === "!cat") {
 *         const r = await MediaSharer.send(msg, "https://example.com/cat.jpg", { leave: "home" });
 *         if (!r.ok) msg.room.send("Failed: " + r.error);
 *     }
 * }
 * ```
 *
 * [send] returns at once and resolves to a [MediaSendResult] (a Promise in NeonJS, a CompletableFuture in Rhino: `.join()`
 * waits), so the project keeps handling messages while files download.
 *
 * - room: `msg`, `msg.room`, a channel ID (text or BigInt) or the name of a room a message came from. In the debug
 *   room, `msg` and `msg.room` show the files in the chat instead.
 * - files: a path (relative to the project directory), an http(s) URL, a data URL, Base64 text, a Java `byte[]`, a
 *   `Uint8Array`/`ArrayBuffer`, a `Bitmap` (such as `msg.image`), or an array of these.
 * - options: `{ fileName, timeout, cache, headers, leave }`.
 *
 * Only the script-facing methods live here; StarLight's editor lists every member of this class.
 */
class MediaSharer internal constructor(project: Project) {

    private val core = MediaSharerCore(project)

    /** Sends [files] to [room]. Resolves to a [MediaSendResult]; failures are logged to the project too. */
    fun send(room: Any?, files: Any?): CompletableFuture<MediaSendResult> =
        core.send(room, files, null)

    /**
     * [options]:
     * - `fileName`: name to send under, an extension is added (`name_1`, `name_2`, ... for several files)
     * - `timeout`: download timeout in milliseconds
     * - `cache`: keep downloaded or decoded files to send again without downloading
     * - `headers`: download request headers, such as `{ Referer: "..." }`
     * - `leave`: once KakaoTalk has read the files, leave it for `"home"` (or `true`), `"starlight"` or a package
     *   name; `"stay"` (or `false`) stays. Absent: the setting in Settings > Media sending.
     */
    fun send(room: Any?, files: Any?, options: Any?): CompletableFuture<MediaSendResult> =
        core.send(room, files, options)

    /**
     * Leaves KakaoTalk for the home screen once it has read the files of the latest send, waiting for sends still
     * being prepared. While a chat room is open, KakaoTalk posts no notifications, so messages meanwhile are missed.
     */
    fun leave(): Boolean =
        core.leave(null, null)

    /** Leaves KakaoTalk for [to] (`"home"`, `"starlight"` or a package name), as [leave] does; a number is a delay. */
    fun leave(to: Any?): Boolean =
        core.leave(to, null)

    /** Leaves KakaoTalk for [to] after [delay] milliseconds instead. */
    fun leave(to: Any?, delay: Any?): Boolean =
        core.leave(to, delay)

    /** Deletes every cached file; returns how many. */
    fun clearCache(): Int =
        core.clearCache(null)

    /** Deletes the cached file of a URL, a cache key or a cache file name, or of each in an array; returns how many. */
    fun clearCache(target: Any?): Int =
        core.clearCache(target)

    /** The KakaoTalk rooms messages came from, last seen first. */
    fun getRooms(): List<RoomInfo> =
        core.rooms()

    /** The room last seen with [name], or null if no message came from such a room yet. */
    fun findRoom(name: Any?): RoomInfo? =
        core.findRoom(name)

    /**
     * Whether StarLight may draw over other apps. Without it, Android only lets StarLight open KakaoTalk while
     * StarLight is on screen.
     */
    fun hasOverlayPermission(): Boolean =
        core.hasOverlayPermission()

    override fun toString(): String =
        "MediaSharer"
}
