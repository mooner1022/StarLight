package dev.mooner.starlight.ui.editor

interface WebviewCallback {

    fun onLoadComplete()

    fun onContentChanged(sessionId: String?, code: String?)

    fun requestSession(sessionId: String?)
}