package com.mooner.starlight.plugincore.api

interface ApiObject {
    val name: String
    val returns: Class<*>
}