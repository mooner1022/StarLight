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
     * Sends [files] to [room], as [options] say. Resolves to a [MediaSendResult]; failures are logged to the project too.
     *
     * [options] is an object with any of these; one left out takes its setting in **미디어 전송**:
     * - `fileName`: name to send under, an extension is added (`name_1`, `name_2`, ... for several files)
     * - `timeout`: download timeout in milliseconds; otherwise **다운로드 제한 시간 (초)**
     * - `cache`: keep downloaded or decoded files to send again without downloading; otherwise **기본으로 캐시 사용**
     * - `headers`: download request headers, such as `{ Referer: "..." }`
     * - `leave`: once KakaoTalk has read the files, leave it for `"home"` (or `true`), `"starlight"` or a package
     *   name; `"stay"` (or `false`) stays. Otherwise **전송 후**.
     */
    fun send(room: Any?, files: Any?, options: Any?): CompletableFuture<MediaSendResult> =
        core.send(room, files, options)

    /**
     * Sends [text] to [room] through KakaoTalk's share screen. Resolves to a [MediaSendResult] once KakaoTalk was
     * started, which then sends it; failures are logged to the project too.
     *
     * Unlike `room.send(text)`, which answers through the notification, this opens KakaoTalk and the room, as sending
     * files does, so it works for any room a message came from and can format the text: see the options below. In the
     * debug room the text is shown plainly.
     *
     * While the room is open, KakaoTalk posts no notifications for it, so its messages don't reach scripts until
     * KakaoTalk is left: see **전송 후** and the `leave` option.
     */
    fun sendText(room: Any?, text: Any?): CompletableFuture<MediaSendResult> =
        core.sendText(room, text, null, toMemo = false)

    /**
     * Sends [text] to [room] through KakaoTalk's share screen, as [options] say. Resolves to a [MediaSendResult] once
     * KakaoTalk was started, which then sends it; failures are logged to the project too.
     *
     * [options] is an object with any of these:
     * - `markdown`: `true` to show the text as Markdown, as `**bold**`, `*italic*`, `` `code` `` and `- list`
     * - `spoilers`: parts hidden until tapped, each `{ start, length }` or `[start, length]`, counted in characters from
     *   0. KakaoTalk shows no spoilers in a Markdown message, so the two can't be combined.
     * - `emoticons`: mini emoticons, each its ID (`"<item code>_<number>"`) for the next placeholder, or
     *   `{ id, at, length }` for the placeholder at the `at`-th `(` of the text. A placeholder is the text from a `(` to
     *   the next `)`, such as `(heart)`, 1 to 12 characters. KakaoTalk sends only mini emoticons the account has; others
     *   stay as their placeholder text.
     * - `leave`: as for [send], where to go once KakaoTalk has had time to send
     *
     * ```js
     * MediaSharer.sendText(msg, "**Done** in `3s`", { markdown: true });
     * MediaSharer.sendText(msg, "Answer: spoiler", { spoilers: [{ start: 8, length: 7 }] });
     * ```
     *
     * KakaoTalk refuses secret chats, channel chats and silent rooms. Whether KakaoTalk's servers keep the formatting
     * for the other people in the room isn't something StarLight can check.
     */
    fun sendText(room: Any?, text: Any?, options: Any?): CompletableFuture<MediaSendResult> =
        core.sendText(room, text, options, toMemo = false)

    /**
     * Sends [text] to the memo chat (나와의 채팅) of the KakaoTalk in StarLight's own Android user, not a Dual Messenger
     * copy. KakaoTalk sends it at once without showing a screen, and shows a toast.
     */
    fun sendTextToMemo(text: Any?): CompletableFuture<MediaSendResult> =
        core.sendText(null, text, null, toMemo = true)

    /** Sends [text] to the memo chat (나와의 채팅), with the options of [sendText]; `leave` is ignored. */
    fun sendTextToMemo(text: Any?, options: Any?): CompletableFuture<MediaSendResult> =
        core.sendText(null, text, options, toMemo = true)

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
