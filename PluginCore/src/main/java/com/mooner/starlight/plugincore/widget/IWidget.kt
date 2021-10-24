package com.mooner.starlight.plugincore.widget

import android.view.View

interface IWidget {

    val id: String

    val name: String

    val size: WidgetSize

    fun onCreateWidget(view: View)

    fun onPauseWidget() {}

    fun onResumeWidget() {}

    fun onDestroyWidget() {}

    fun onCreateThumbnail(view: View)

    fun onPauseThumbnail() {}

    fun onResumeThumbnail() {}

    fun onDestroyThumbnail() {}
}