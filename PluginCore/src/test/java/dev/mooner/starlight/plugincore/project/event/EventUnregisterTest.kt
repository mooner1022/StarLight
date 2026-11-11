package dev.mooner.starlight.plugincore.project.event

import dev.mooner.starlight.plugincore.language.CodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Taking an event out again, as when the plugin that registered it failed to load. */
class EventUnregisterTest {

    class One : ProjectEvent() {
        override val id = "one"
        override val name = "one"
        override val functionName = "onOne"
        override val argTypes: Array<CodeGenerator.Argument<*>> = emptyArray()
    }

    class Two : ProjectEvent() {
        override val id = "two"
        override val name = "two"
        override val functionName = "onTwo"
        override val argTypes: Array<CodeGenerator.Argument<*>> = emptyArray()
    }

    @Test
    fun `an unregistered event is gone and its neighbours stay`() {
        ProjectEventManager.register("unregtest.group.one", One::class)
        ProjectEventManager.register("unregtest.group.two", Two::class)
        assertTrue("unregtest.group.one" in ProjectEventManager.registeredIds())

        ProjectEventManager.unregister("unregtest.group.one")

        assertFalse(ProjectEventManager.isIdUsed("unregtest.group.one"))
        assertNull(ProjectEventManager.validateAndGetEventID(One::class))
        assertEquals(setOf("unregtest.group.two"), ProjectEventManager.filterAllowedEvents(setOf("unregtest.*")))

        // The id is free again, as for a plugin that loads the next time.
        assertTrue(ProjectEventManager.register("unregtest.group.one", One::class))
        ProjectEventManager.unregister("unregtest.group.one")
        ProjectEventManager.unregister("unregtest.group.two")
        assertTrue(ProjectEventManager.filterAllowedEvents(setOf("unregtest.*")).isEmpty())
    }

    @Test
    fun `unregistering what was never registered changes nothing`() {
        val before = ProjectEventManager.registeredIds()

        ProjectEventManager.unregister("unregtest.never.there")
        ProjectEventManager.unregister("unregtest")

        assertEquals(before, ProjectEventManager.registeredIds())
    }
}
