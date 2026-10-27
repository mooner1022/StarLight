package dev.mooner.starlight.plugincore.widget

import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import kotlinx.serialization.json.JsonElement

private val logger = LoggerFactory.logger { }

abstract class WidgetController internal constructor() {

    var onError: ((Throwable) -> Unit)? = null

    abstract fun create(
        container: FrameLayout,
        span: WidgetSpan,
        isPreview: Boolean,
        settings: Map<String, JsonElement> = emptyMap(),
    )

    abstract fun resize(span: WidgetSpan)

    open fun updateSettings(settings: Map<String, JsonElement>) {}

    abstract fun attach(lifecycle: Lifecycle)

    abstract fun destroy()

    internal fun reportError(throwable: Throwable) {
        onError?.invoke(throwable)
            ?: logger.error(throwable) { "Unhandled widget error" }
    }

    internal fun guard(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            reportError(e)
        }
    }
}

internal class DslWidgetController(
    private val definition: DslWidgetDefinition,
) : WidgetController(), LifecycleOwner {

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    private val observer = LifecycleEventObserver { _, event ->
        registry.handleLifecycleEvent(event)
    }

    private var parent: Lifecycle? = null
    private var container: FrameLayout? = null
    private var content: WidgetContent? = null
    private var scope: WidgetScope? = null
    private var settings: Map<String, JsonElement> = emptyMap()
    private var isPreview = false
    private var isDestroyed = false

    override fun create(container: FrameLayout, span: WidgetSpan, isPreview: Boolean, settings: Map<String, JsonElement>) {
        this.container = container
        this.isPreview = isPreview
        this.settings = settings
        content = if (isPreview)
            definition.preview ?: definition.content
        else
            definition.content
        render(span)
    }

    private fun render(span: WidgetSpan) {
        val container = container ?: return
        val content = content ?: return
        scope?.dispose()
        container.removeAllViews()
        val widgetSettings = definition.settingsOf(settings)
        scope = WidgetScope(container, span, isPreview, widgetSettings, this, ::reportError).also { scope ->
            guard { content(scope) }
        }
    }

    override fun resize(span: WidgetSpan) {
        val scope = scope ?: return
        if (scope.span == span)
            return
        if (scope.hasResizeListener)
            guard { scope.dispatchResize(span) }
        else
            render(span)
    }

    override fun updateSettings(settings: Map<String, JsonElement>) {
        this.settings = settings
        scope?.let { render(it.span) }
    }

    override fun attach(lifecycle: Lifecycle) {
        check(parent == null) { "Widget ${definition.id} is already attached" }
        parent = lifecycle
        lifecycle.addObserver(observer)
    }

    override fun destroy() {
        if (isDestroyed)
            return
        isDestroyed = true
        parent?.removeObserver(observer)
        parent = null
        scope?.dispose()
        scope = null
        if (registry.currentState != Lifecycle.State.INITIALIZED)
            registry.currentState = Lifecycle.State.DESTROYED
        container = null
    }
}

internal class LegacyWidgetController(
    private val widget: Widget,
) : WidgetController() {

    private val observer = LifecycleEventObserver { source, event ->
        guard { widget.onStateChanged(source, event) }
        when (event) {
            Lifecycle.Event.ON_RESUME -> guard(widget::onResumeWidget)
            Lifecycle.Event.ON_PAUSE  -> guard(widget::onPauseWidget)
            else -> {}
        }
    }

    private var parent: Lifecycle? = null
    private var isPreview = false
    private var isCreated = false
    private var isDestroyed = false

    override fun create(container: FrameLayout, span: WidgetSpan, isPreview: Boolean, settings: Map<String, JsonElement>) {
        this.isPreview = isPreview
        guard {
            if (isPreview)
                widget.onCreateThumbnail(container)
            else
                widget.onCreateWidget(container)
        }
        isCreated = true
    }

    override fun resize(span: WidgetSpan) {}

    override fun attach(lifecycle: Lifecycle) {
        check(parent == null) { "Widget ${widget.id} is already attached" }
        parent = lifecycle
        lifecycle.addObserver(observer)
    }

    override fun destroy() {
        if (isDestroyed)
            return
        isDestroyed = true
        parent?.let { parent ->
            parent.removeObserver(observer)
            val state = widget.lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED))
                guard(widget::onPauseWidget)
            if (state != Lifecycle.State.INITIALIZED && state != Lifecycle.State.DESTROYED)
                guard { widget.onStateChanged(widget, Lifecycle.Event.ON_DESTROY) }
        }
        parent = null
        if (!isCreated)
            return
        guard {
            if (isPreview)
                widget.onDestroyThumbnail()
            else
                widget.onDestroyWidget()
        }
    }
}
