package dev.mooner.starlight.ui.editor

interface WebviewCallback {

    fun onLoadComplete()

    fun onContentChanged(code: String?)
}