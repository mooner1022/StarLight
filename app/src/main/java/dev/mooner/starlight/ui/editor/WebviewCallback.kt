package dev.mooner.starlight.ui.editor

interface WebviewCallback {

    fun onLoadComplete()

    fun onDirtyChanged(sessionId: String, dirty: Boolean)

    fun onAnnotationUpdated(list: String)

    fun onEditorBackground(color: String)
}