package com.mooner.starlight.plugincore.binding

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

object UpdateRequestHandler {

    //const val LAYOUT_HOME = 0
    const val LAYOUT_PROJECTS = 1
    const val LAYOUT_PLUGINS = 2

    private val updateListeners: ConcurrentMap<Int, () -> Unit> = ConcurrentHashMap()


}