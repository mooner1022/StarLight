package com.mooner.starlight.plugincore

import com.mooner.starlight.plugincore.logger.Logger

internal object Session {
    val logger: Logger = Logger()
    private var lastRoom: String? = null
    private var listener: ((room: String, msg: String) -> Unit)? = null

    fun bindReplier(block: (room: String, msg: String) -> Unit) {
        this.listener = block
    }

    fun reply(msg: String) {
        if (listener != null) {
            if (lastRoom == null) throw IllegalStateException("reply() called before init")
            listener!!(lastRoom!!, msg)
        }
    }

    fun reply(room: String, msg: String) {
        if (listener != null) {
            listener!!(room, msg)
        }
    }
}

fun getLogger(): Logger = Session.logger