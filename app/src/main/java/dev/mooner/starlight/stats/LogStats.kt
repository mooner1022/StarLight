package dev.mooner.starlight.stats

import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object LogStats {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isStarted = AtomicBoolean(false)

    private val errors = AtomicInteger()
    private val warnings = AtomicInteger()

    private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val updates: SharedFlow<Unit>
        get() = _updates

    var startedMillis: Long = 0L
        private set

    val errorCount: Int
        get() = errors.get()

    val warningCount: Int
        get() = warnings.get()

    fun start() {
        if (!isStarted.compareAndSet(false, true))
            return
        startedMillis = System.currentTimeMillis()
        EventHandler.on(scope, ::onLog)
    }

    private fun onLog(event: Events.Log.Create) {
        when (event.log.type) {
            LogType.ERROR, LogType.CRITICAL -> errors.incrementAndGet()
            LogType.WARN                    -> warnings.incrementAndGet()
            else                            -> return
        }
        _updates.tryEmit(Unit)
    }
}
