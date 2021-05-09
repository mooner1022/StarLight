package com.mooner.starlight.plugincore.editor

import java.io.File

abstract class Editor: IEditor {
    private var _code: String? = null
    val code: String
        get() = _code!!
    private var _callback: ((code: String) -> Unit)? = null

    override fun init(code: String) {
        this._code = code
    }

    override fun init(path: File) {
        if (!path.exists()) {
            throw IllegalStateException("${path.name} does not exist")
        }
        if (!path.isFile) {
            throw IllegalStateException("${path.name} is not a file")
        }
        val content = path.readText()
        this._code = content
    }

    override fun setOnSaveCallback(callback: (code: String) -> Unit) {
        this._callback = callback
    }

    fun saveCode(code: String) {
        if (_callback != null) {
            _callback!!(code)
        }
    }
}