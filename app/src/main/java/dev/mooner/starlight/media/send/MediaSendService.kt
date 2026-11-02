/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.send

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.media.media.Downloader
import dev.mooner.starlight.media.media.MediaCache
import dev.mooner.starlight.media.media.MediaPreparer
import dev.mooner.starlight.media.room.DirectRoom
import dev.mooner.starlight.media.room.RoomRegistry
import dev.mooner.starlight.media.room.RoomResolver
import dev.mooner.starlight.media.room.Target
import dev.mooner.starlight.media.room.starLightChatRoomOf
import dev.mooner.starlight.plugincore.chat.MediaFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

/**
 * Sends files to KakaoTalk: prepares every file in parallel, then starts KakaoTalk's share screen for the room. A room
 * that shows files itself, such as the debug room, gets the prepared files instead.
 *
 * Layout of [dataDir] (inside the app's files directory, which its FileProvider serves):
 * - `cache/`: [MediaCache]
 * - `outgoing/<send>/`: copies and downloads of one send, deleted [SendSettings.tempRetentionMillis] after it
 * - `rooms.json`: [RoomRegistry]
 */
class MediaSendService(
    dataDir: File,
    private val settings: () -> SendSettings,
    private val scope: CoroutineScope,
) {

    val rooms = RoomRegistry(dataDir.resolve("rooms.json"))
    val resolver = RoomResolver(rooms, ::starLightChatRoomOf)
    val cache = MediaCache(dataDir.resolve("cache"), {
        settings().let { MediaCache.Limits(it.cacheMaxItems, it.cacheMaxBytes, it.tempRetentionMillis) }
    })
    val history = SendHistory()

    private val preparer = MediaPreparer(cache, Downloader())
    private val outgoingDir = dataDir.resolve("outgoing")
    private val sendLock = Mutex()
    private val sendIds = AtomicLong()

    @Volatile
    private var lastSentAt = 0L

    /** Sends between their start and the end of [send]: each opens KakaoTalk once its files are ready. */
    private val activeSends = MutableStateFlow(0)

    /** The latest share handed to KakaoTalk, whose reading tells when KakaoTalk can be left. */
    private class Share(val watch: ReadWatch, val startedAt: Long, val needsChoice: Boolean)

    @Volatile
    private var lastShare: Share? = null

    private val leaveLock = Any()
    private var leaveJob: Job? = null

    /**
     * Loads known rooms and removes files a previous run left to be deleted. The old files are moved aside first, so
     * a send started right away doesn't lose its own.
     */
    fun start() {
        rooms.load()
        val parent = outgoingDir.parentFile ?: return
        if (outgoingDir.exists())
            outgoingDir.renameTo(parent.resolve("$TRASH_PREFIX${System.currentTimeMillis()}"))
        scope.launch(Dispatchers.IO) {
            parent.listFiles { file -> file.name.startsWith(TRASH_PREFIX) }?.forEach { it.deleteRecursively() }
        }
    }

    fun stop() {
        lastShare?.watch?.stop()
        rooms.save()
        runCatching { cache.flush() }
    }

    /**
     * Starts sending [request] on the plugin's scope. It counts as active from this call on, so leaving KakaoTalk
     * right after waits for it. Files that fail are reported to [log] and left out; the future fails with a
     * [SendException] if nothing could be sent.
     */
    fun send(request: SendRequest, log: SendLog): CompletableFuture<SendOutcome> {
        val future = CompletableFuture<SendOutcome>()
        activeSends.update { it + 1 }
        val job = scope.launch {
            try {
                future.complete(doSend(request, log))
            } catch (e: Throwable) {
                future.completeExceptionally(e)
                if (e is CancellationException)
                    throw e
            }
        }
        // Also when the plugin is disabled before the send started.
        job.invokeOnCompletion { cause ->
            activeSends.update { it - 1 }
            if (cause != null)
                future.completeExceptionally(SendException("Sending was stopped"))
        }
        return future
    }

    private suspend fun doSend(request: SendRequest, log: SendLog): SendOutcome {
        val startedAt = System.currentTimeMillis()
        val settings = settings()
        var sent = 0
        val failures = mutableListOf<String>()
        var error: String? = null
        var via: String? = null
        try {
            val context = GlobalApplication.requireContext()
            val work = MediaPreparer.WorkDir(outgoingDir.resolve("$startedAt-${sendIds.incrementAndGet()}"))
            val options = MediaPreparer.Options(
                useCache = request.useCache ?: settings.cacheByDefault,
                timeoutMillis = request.timeoutMillis ?: settings.downloadTimeoutMillis,
                maxBytes = settings.maxFileBytes,
                headers = request.headers,
            )

            // A URL or path given twice is prepared once and sent twice.
            val unique = request.sources.distinctBy { it.key ?: it }
            val indexOf = request.sources.map { source -> unique.indexOfFirst { it === source || (source.key != null && it.key == source.key) } }
            val isMultiple = request.sources.size > 1
            val results = coroutineScope {
                unique.mapIndexed { i, source ->
                    async(Dispatchers.IO) {
                        runCatching {
                            val name = request.fileName?.let { if (isMultiple) "${it}_${i + 1}" else it }
                            preparer.prepare(source, name, work, options, i + 1)
                        }
                    }
                }.awaitAll()
            }
            val errors = arrayOfNulls<String>(unique.size)
            val direct = request.target as? DirectRoom
            val shared: List<SharedFile?> = withContext(Dispatchers.IO) {
                results.mapIndexed { i, result ->
                    try {
                        val prepared = result.getOrThrow()
                        // A room that shows files itself reads them in place; KakaoTalk needs a URI it may read.
                        if (direct != null)
                            SharedFile(null, prepared.mime, prepared.file)
                        else
                            sharedFile(context, prepared.file, prepared.mime, work)
                    } catch (e: Exception) {
                        errors[i] = e.message ?: e.toString()
                        null
                    }
                }
            }
            errors.forEachIndexed { i, message ->
                if (message != null) {
                    failures += "${unique[i].label}: $message"
                    log.warn("${unique[i].label}: $message")
                }
            }

            val files = indexOf.mapNotNull { shared[it] }
            if (files.isEmpty()) {
                work.dir.deleteRecursively()
                val reason = errors.firstOrNull { it != null }
                throw SendException(if (unique.size == 1 && reason != null) reason else "None of the ${unique.size} files could be prepared")
            }

            if (direct != null) {
                sent = deliver(direct, files)
                work.dir.deleteRecursively()
                if (settings.logSends)
                    log.info("Sent $sent file(s) to ${direct.label}")
                return SendOutcome(sent, failures.toList(), null)
            }
            val target = request.target as Target

            // Watched from before KakaoTalk can read them, to leave KakaoTalk as soon as it has.
            val watch = ReadWatch(files.map { it.file })
            val route = sendLock.withLock {
                val wait = lastSentAt + settings.sendIntervalMillis - System.currentTimeMillis()
                if (wait > 0)
                    delay(wait)
                watch.start()
                val route = try {
                    withContext(Dispatchers.IO) {
                        KakaoShare.share(context, target.channelId, files.map { requireNotNull(it.uri) to it.mime }, target.userId)
                    }
                } catch (e: Throwable) {
                    watch.stop()
                    work.dir.deleteRecursively()
                    if (e is ActivityNotFoundException)
                        throw SendException("KakaoTalk is not installed or has no share screen")
                    throw e
                }
                lastSentAt = System.currentTimeMillis()
                lastShare = Share(watch, lastSentAt, needsChoice = route is KakaoShare.Route.Chooser)
                route
            }
            releaseWatch(watch, lastSentAt, settings, log)
            if (route is KakaoShare.Route.Chooser)
                log.warn("${target.label}: Android asks which KakaoTalk to use, so the files wait for a choice: ${route.reason}")
            via = when (route) {
                KakaoShare.Route.Direct      -> null
                is KakaoShare.Route.OtherUser -> "user ${route.userId}"
                is KakaoShare.Route.Chooser  -> "chooser"
            }
            sent = files.size
            deleteLater(work.dir, settings.tempRetentionMillis)

            leaveKakao(request.leaveTo ?: settings.leaveTo, LeaveTiming.AfterRead)
            if (settings.logSends)
                log.info("Sent $sent file(s) to ${target.label}" + (via?.let { " via $it" } ?: ""))
            return SendOutcome(sent, failures.toList(), via)
        } catch (e: Throwable) {
            error = e.message ?: e.toString()
            throw e
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { cache.flush() } }
            history.record(
                SendHistory.Record(
                    time = startedAt,
                    project = log.projectName,
                    room = request.target.label,
                    files = sent,
                    failed = failures.size,
                    durationMillis = System.currentTimeMillis() - startedAt,
                    error = error,
                    via = via,
                )
            )
        }
    }

    /**
     * Leaves KakaoTalk's chat room, in which KakaoTalk posts no notifications, for [to]. A newer request replaces one
     * still waiting; [LeaveTo.Stay] does nothing.
     */
    fun leaveKakao(to: LeaveTo, timing: LeaveTiming) {
        val context = GlobalApplication.requireContext()
        val packageName = when (to) {
            LeaveTo.Stay      -> null
            LeaveTo.Home      -> null
            LeaveTo.StarLight -> context.packageName
            is LeaveTo.App    -> to.packageName
        }
        synchronized(leaveLock) {
            if (to == LeaveTo.Stay) {
                // Staying after this send must not cancel leaving after an earlier one.
                return
            }
            leaveJob?.cancel()
            leaveJob = scope.launch {
                val shouldLeave = when (timing) {
                    is LeaveTiming.After  -> {
                        delay(timing.millis.coerceAtLeast(0))
                        true
                    }
                    LeaveTiming.AfterRead -> awaitShareRead()
                }
                if (shouldLeave)
                    KakaoShare.leaveKakao(context, packageName)
            }
        }
    }

    /**
     * Waits until KakaoTalk has read the files of the latest share, plus [SendSettings.leaveGraceMillis], for at most
     * [SendSettings.leaveMaxWaitMillis] after KakaoTalk was opened. Returns false if the share waits for the user to
     * pick a KakaoTalk and was never read: leaving would close the chooser.
     */
    private suspend fun awaitShareRead(): Boolean {
        // A send still being prepared opens KakaoTalk once ready: that is the share to wait for.
        activeSends.first { it == 0 }
        val share = lastShare ?: return true
        val settings = settings()
        val limit = if (share.needsChoice) settings.tempRetentionMillis else settings.leaveMaxWaitMillis
        val readAt = share.watch.awaitRead(share.startedAt + limit - System.currentTimeMillis())
            ?: return !share.needsChoice
        delay(readAt + settings.leaveGraceMillis - System.currentTimeMillis())
        return true
    }

    /** Stops watching [watch] once its files were read, or once they are deleted. */
    private fun releaseWatch(watch: ReadWatch, startedAt: Long, settings: SendSettings, log: SendLog) {
        scope.launch {
            val readAt = watch.awaitRead(settings.tempRetentionMillis)
            watch.stop()
            if (settings.logSends)
                log.info(if (readAt != null) "KakaoTalk read the files in ${readAt - startedAt}ms" else "KakaoTalk's reading of the files wasn't seen")
        }
    }

    /** A prepared file as shared: its URI from the app's FileProvider (null for a room that reads files itself). */
    private class SharedFile(val uri: Uri?, val mime: String, val file: File)

    /** Hands [files] to a room that shows them itself, which copies what it keeps before the call returns. */
    private suspend fun deliver(target: DirectRoom, files: List<SharedFile>): Int {
        val show = target.room.onSendMedia ?: throw SendException("This debug room can't show files")
        withContext(Dispatchers.IO) {
            show(files.map { MediaFile(it.file, it.mime) })
        }
        return files.size
    }

    /** [file] with a URI KakaoTalk can read, copied first if StarLight's FileProvider can't serve it. */
    private fun sharedFile(context: Context, file: File, mime: String, work: MediaPreparer.WorkDir): SharedFile =
        try {
            SharedFile(KakaoShare.uriFor(context, file), mime, file)
        } catch (_: IllegalArgumentException) {
            val copy = work.reserve(file.name)
            MediaPreparer.linkOrCopy(file, copy)
            SharedFile(KakaoShare.uriFor(context, copy), mime, copy)
        }

    private fun deleteLater(dir: File, delayMillis: Long) {
        scope.launch(Dispatchers.IO) {
            delay(delayMillis)
            dir.deleteRecursively()
        }
    }

    private companion object {
        const val TRASH_PREFIX = "outgoing-old-"
    }
}
