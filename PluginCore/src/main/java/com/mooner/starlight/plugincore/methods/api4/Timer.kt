package com.mooner.starlight.plugincore.methods.api4

import java.util.Timer
import kotlin.concurrent.schedule

class Timer {
    companion object {
        fun schedule(millis: Long, callback: () -> Unit): Timer {
            return Timer().apply { schedule(millis) { callback() } }
        }
    }
}