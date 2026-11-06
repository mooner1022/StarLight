package dev.mooner.starlight.stats

import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object MessageStats {

    const val WINDOW_MINUTES = 60

    private const val MINUTE_MILLIS = 60_000L

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isStarted = AtomicBoolean(false)

    private val minuteCounts: ConcurrentHashMap<Long, AtomicInteger> = ConcurrentHashMap()
    private val roomCounts: ConcurrentHashMap<Long, ConcurrentHashMap<String, AtomicInteger>> = ConcurrentHashMap()
    private val total = AtomicLong()

    private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val updates: SharedFlow<Unit>
        get() = _updates

    val totalCount: Long
        get() = total.get()

    fun start() {
        if (isStarted.compareAndSet(false, true))
            EventHandler.on(scope, ::onMessage)
    }

    fun recentCounts(now: Long = System.currentTimeMillis()): IntArray {
        val currentMinute = now / MINUTE_MILLIS
        return IntArray(WINDOW_MINUTES) { index ->
            minuteCounts[currentMinute - (WINDOW_MINUTES - 1 - index)]?.get() ?: 0
        }
    }

    fun topRooms(limit: Int, now: Long = System.currentTimeMillis()): List<Pair<String, Int>> {
        val oldestMinute = now / MINUTE_MILLIS - (WINDOW_MINUTES - 1)
        val totals: MutableMap<String, Int> = hashMapOf()
        for ((minute, rooms) in roomCounts) {
            if (minute < oldestMinute)
                continue
            for ((room, count) in rooms)
                totals.merge(room, count.get(), Int::plus)
        }
        return totals.entries
            .map { (room, count) -> room to count }
            .sortedByDescending { (_, count) -> count }
            .take(limit)
    }

    internal fun record(roomName: String, now: Long = System.currentTimeMillis()) {
        val minute = now / MINUTE_MILLIS
        minuteCounts.computeIfAbsent(minute) { AtomicInteger() }.incrementAndGet()
        roomCounts.computeIfAbsent(minute) { ConcurrentHashMap() }
            .computeIfAbsent(roomName) { AtomicInteger() }
            .incrementAndGet()
        minuteCounts.keys.removeAll { it <= minute - WINDOW_MINUTES }
        roomCounts.keys.removeAll { it <= minute - WINDOW_MINUTES }
        total.incrementAndGet()
        _updates.tryEmit(Unit)
    }

    private fun onMessage(event: Events.Message.Create) =
        record(event.message.room.name)
}
