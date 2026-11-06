package dev.mooner.starlight.plugincore.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.mooner.starlight.plugincore.event.Event
import dev.mooner.starlight.plugincore.event.EventHandler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

@DslMarker
annotation class WidgetDsl

typealias WidgetContent = WidgetScope.() -> Unit

@WidgetDsl
class WidgetScope internal constructor(
    val container: FrameLayout,
    span: WidgetSpan,
    val isPreview: Boolean,
    val settings: WidgetSettings,
    owner: LifecycleOwner,
    onError: (Throwable) -> Unit,
) : LifecycleOwner by owner {

    val context: Context
        get() = container.context

    var span: WidgetSpan = span
        private set

    val coroutineScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, t -> onError(t) }
    )

    private var resizeListener: ((WidgetSpan) -> Unit)? = null

    internal val hasResizeListener: Boolean
        get() = resizeListener != null

    fun inflate(@LayoutRes layoutRes: Int): View {
        container.removeAllViews()
        return LayoutInflater.from(context)
            .inflate(layoutRes, container, false)
            .also(container::addView)
    }

    fun <T> inflate(factory: (LayoutInflater, ViewGroup, Boolean) -> T): T {
        container.removeAllViews()
        return factory(LayoutInflater.from(context), container, true)
    }

    fun <T : View> view(@IdRes id: Int): T =
        container.findViewById(id)

    fun launch(block: suspend CoroutineScope.() -> Unit): Job =
        coroutineScope.launch(block = block)

    fun repeatWhile(state: Lifecycle.State, block: suspend CoroutineScope.() -> Unit): Job =
        launch { lifecycle.repeatOnLifecycle(state, block) }

    fun ticker(period: Duration, block: suspend () -> Unit): Job =
        repeatWhile(Lifecycle.State.RESUMED) {
            while (isActive) {
                block()
                delay(period)
            }
        }

    inline fun <reified T : Event> on(noinline block: suspend T.() -> Unit): Job =
        repeatWhile(Lifecycle.State.STARTED) {
            EventHandler.eventFlow
                .filterIsInstance<T>()
                .collect { block(it) }
        }

    fun onResize(block: (span: WidgetSpan) -> Unit) {
        resizeListener = block
    }

    internal fun dispatchResize(span: WidgetSpan) {
        this.span = span
        resizeListener?.invoke(span)
    }

    internal fun dispose() {
        coroutineScope.cancel()
    }
}
