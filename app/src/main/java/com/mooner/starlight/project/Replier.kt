package com.mooner.starlight.project

interface Replier {
    fun reply(msg: String)

    fun replyTo(room: String, msg: String)
}