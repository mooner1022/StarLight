package com.mooner.starlight.plugincore.project

interface Replier {
    fun reply(msg: String)

    fun reply(room: String, msg: String)
}