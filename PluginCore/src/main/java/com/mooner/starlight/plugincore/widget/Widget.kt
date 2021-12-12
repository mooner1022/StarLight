package com.mooner.starlight.plugincore.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

abstract class Widget {

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

    protected fun View.updateHeight() {
        assert(this@Widget.size == WidgetSize.Wrap) { "Widget size should be WidgetSize.Wrap to use updateHeight()" }
        assert(Thread.currentThread().name == "main") { "View function should only be accessed from main thread" }
        params {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
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