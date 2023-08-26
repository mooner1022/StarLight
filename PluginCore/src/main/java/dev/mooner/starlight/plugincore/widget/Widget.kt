package dev.mooner.starlight.plugincore.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

abstract class Widget: LifecycleOwner, LifecycleEventObserver {

    private val lifecycleRegistry = LifecycleRegistry(this)

    abstract val id: String

    abstract val name: String

    open val size: WidgetSize = WidgetSize.Medium

    abstract fun onCreateWidget(view: View)

    open fun onPauseWidget() {}

    open fun onResumeWidget() {}

    open fun onDestroyWidget() {}

    abstract fun onCreateThumbnail(view: View)

    open fun onDestroyThumbnail() {}

    override fun equals(other: Any?): Boolean = when(other) {
        null -> false
        is Widget -> other.id == this.id
        else -> false
    }

    override fun hashCode(): Int = this.id.hashCode()

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        try {
            lifecycleRegistry.handleLifecycleEvent(event)
        } catch (e: Exception) {
            println("Failed to handle lifecycle event: $e")
            e.printStackTrace()
        }
    }

    protected fun View.updateHeight() {
        require(this@Widget.size == WidgetSize.Wrap) { "Widget size should be WidgetSize.Wrap to use updateHeight()" }
        require(Thread.currentThread().name == "main") { "View function should only be accessed from main thread" }
        params {
            //width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        requestLayout()
    }

    private fun View.params(block: FrameLayout.LayoutParams.() -> Unit) {
        val params = if (layoutParams == null) {
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply(block)
        } else {
            (layoutParams as FrameLayout.LayoutParams).apply(block)
        }
        layoutParams = params
    }
}