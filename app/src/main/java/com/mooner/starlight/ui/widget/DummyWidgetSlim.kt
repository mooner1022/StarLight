package com.mooner.starlight.ui.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.mooner.starlight.plugincore.widget.Widget
import com.mooner.starlight.plugincore.widget.WidgetSize

class DummyWidgetSlim: Widget() {

    override val id: String = "dummy_slim"
    override val name: String = "더미 위젯(슬림)"
    override val size: WidgetSize = WidgetSize.Slim

    override fun onCreateWidget(view: View) {
        val context = view.context
        val params: FrameLayout.LayoutParams =
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.marginStart = 100
        val textView = TextView(context).apply {
            text = "Hello, World!"
            textSize = 18f
            layoutParams = params
        }
        (view as FrameLayout).addView(textView)
    }

    override fun onCreateThumbnail(view: View) {

    }
}