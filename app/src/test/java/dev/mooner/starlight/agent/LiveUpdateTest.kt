package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.runtime.AgentNotifier
import dev.mooner.starlight.agent.runtime.LiveRun
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A run's progress asks to be a Live Update only while it is the one run and its chat isn't on screen. */
class LiveUpdateTest {

    private val run = LiveRun("c1", LiveRun.Phase.THINKING)

    @Test
    fun promotesOneRunAwayFromItsChat() {
        assertTrue(AgentNotifier.promotes(true, listOf(run), visible = null, dismissed = emptySet()))
        assertTrue(AgentNotifier.promotes(true, listOf(run), visible = "other", dismissed = emptySet()))
    }

    @Test
    fun holdsBackOtherwise() {
        assertFalse("turned off", AgentNotifier.promotes(false, listOf(run), null, emptySet()))
        assertFalse("chat open", AgentNotifier.promotes(true, listOf(run), "c1", emptySet()))
        assertFalse("swiped away", AgentNotifier.promotes(true, listOf(run), null, setOf("c1")))
        assertFalse("several runs", AgentNotifier.promotes(true, listOf(run, LiveRun("c2", LiveRun.Phase.WAITING)), null, emptySet()))
        assertFalse("no run", AgentNotifier.promotes(true, emptyList(), null, emptySet()))
    }
}
