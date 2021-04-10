package com.mooner.starlight.plugincore.project

interface Replier {
    fun reply(msg: String)

    fun replyTo(room: String, msg: String)
}