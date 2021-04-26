package com.mooner.starlight.ui.editor

interface MonacoCallback {

    fun onEditorCreated()

    fun onContentChanged(code: String?)
}