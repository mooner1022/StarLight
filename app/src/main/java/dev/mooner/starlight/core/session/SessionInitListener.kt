package dev.mooner.starlight.core.session

interface SessionInitListener {

    fun onPhaseChanged(phase: String)

    fun onFinished()
}