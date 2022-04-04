package dev.mooner.starlight.plugincore.event

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.logger.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

class EventManager: CoroutineScope {

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
    private var _eventFlow: MutableSharedFlow<Event> = MutableSharedFlow()
    val eventFlow: SharedFlow<Event> get() = _eventFlow.asSharedFlow()

    suspend fun fireEvent(event: Event) = _eventFlow.emit(event)

    fun fireEventWithContext(event: Event, context: CoroutineContext = coroutineContext) = CoroutineScope(context).launch { fireEvent(event) }

    fun fireEventSync(event: Event) = runBlocking(coroutineContext) {
        _eventFlow.emit(event)
    }
}

internal val eventCoroutineScope get() = CoroutineScope(Session.eventManager.coroutineContext + SupervisorJob(Session.eventManager.coroutineContext.job))

public inline fun <reified T: Event> EventManager.on(
    scope: CoroutineScope = this,
    noinline callback: suspend T.() -> Unit
): Job = eventFlow
    .buffer(Channel.UNLIMITED)
    .filterIsInstance<T>()
    .onEach { event ->
        scope.launch(event.coroutineContext) {
            runCatching {
                callback(event)
            }.onFailure { Logger.e(it) }
        }
    }
    .launchIn(scope)