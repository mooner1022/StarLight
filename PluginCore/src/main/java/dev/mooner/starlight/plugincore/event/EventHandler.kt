package dev.mooner.starlight.plugincore.event

import dev.mooner.starlight.plugincore.logger.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

@Deprecated("Retained for legacy support, use EventHandler.")
public typealias EventManager = EventHandler

object EventHandler: CoroutineScope {

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default

    private var eventPublisher: MutableSharedFlow<Event> =
        MutableSharedFlow(extraBufferCapacity = Channel.UNLIMITED)
    val eventFlow: SharedFlow<Event>
        get() = eventPublisher.asSharedFlow()

    suspend fun fireEvent(event: Event) =
        eventPublisher.emit(event)

    fun fireEventWithScope(event: Event, scope: CoroutineScope = this) =
        scope.launch {
            eventPublisher.emit(event)
        }
}

fun eventHandlerScope(): CoroutineScope =
    CoroutineScope(EventHandler.coroutineContext + SupervisorJob(EventHandler.coroutineContext.job))

public inline fun <reified T: Event> EventHandler.on(
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