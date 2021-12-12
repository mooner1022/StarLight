package com.mooner.starlight.core.session

interface SessionInitListener {

    fun onPhaseChanged(phase: String)

    fun onFinished()
}