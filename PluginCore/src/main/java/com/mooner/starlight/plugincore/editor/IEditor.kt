package com.mooner.starlight.plugincore.editor

import java.io.File

interface IEditor {
    fun init(code: String)

    fun init(path: File)

    fun setOnSaveCallback(callback: (code: String) -> Unit)
}