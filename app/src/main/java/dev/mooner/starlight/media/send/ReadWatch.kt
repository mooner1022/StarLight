/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.send

import android.os.FileObserver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Tells when every file of a share has been read: opened at least once, and every open closed again.
 *
 * Events come from inotify, which may merge identical events in a row, so this errs towards finishing early; the
 * caller waits a little more after it.
 */
class ReadTracker(paths: Collection<String>, private val clock: () -> Long = System::currentTimeMillis) {

    private val opens: MutableMap<String, Int> = hashMapOf()
    private val closes: MutableMap<String, Int> = hashMapOf()
    private val pending: MutableSet<String> = paths.toHashSet()

    private val mDone = CompletableDeferred<Long>()

    /** Completes with the time the last file was read. */
    val done: CompletableDeferred<Long>
        get() = mDone

    init {
        if (pending.isEmpty())
            mDone.complete(clock())
    }

    @Synchronized
    fun onOpen(path: String) {
        opens[path] = (opens[path] ?: 0) + 1
    }

    @Synchronized
    fun onClose(path: String) {
        val opened = opens[path] ?: return
        val closed = (closes[path] ?: 0) + 1
        closes[path] = closed
        if (closed >= opened && pending.remove(path) && pending.isEmpty())
            mDone.complete(clock())
    }
}

/**
 * Watches the files of a share for KakaoTalk reading them. KakaoTalk reads through StarLight's FileProvider, which
 * opens the file in StarLight's process; the open file is handed to KakaoTalk and closed once KakaoTalk is done.
 *
 * Start it before KakaoTalk can read, and stop it to release the watches.
 */
class ReadWatch(files: Collection<File>, clock: () -> Long = System::currentTimeMillis) {

    private val tracker = ReadTracker(files.map { it.path }, clock)

    @Suppress("DEPRECATION") // FileObserver(File, Int) needs API 29.
    private val observers: List<FileObserver> = files.distinctBy { it.path }.map { file ->
        object : FileObserver(file.path, EVENTS) {
            override fun onEvent(event: Int, path: String?) {
                when (event and ALL_EVENTS) {
                    OPEN                        -> tracker.onOpen(file.path)
                    CLOSE_NOWRITE, CLOSE_WRITE  -> tracker.onClose(file.path)
                }
            }
        }
    }

    val isRead: Boolean
        get() = tracker.done.isCompleted

    fun start() {
        observers.forEach(FileObserver::startWatching)
    }

    fun stop() {
        observers.forEach(FileObserver::stopWatching)
    }

    /** The time KakaoTalk finished reading, or null if it didn't within [timeoutMillis]. */
    suspend fun awaitRead(timeoutMillis: Long): Long? =
        withTimeoutOrNull(timeoutMillis.coerceAtLeast(0)) { tracker.done.await() }

    private companion object {
        const val EVENTS = FileObserver.OPEN or FileObserver.CLOSE_NOWRITE or FileObserver.CLOSE_WRITE
    }
}
