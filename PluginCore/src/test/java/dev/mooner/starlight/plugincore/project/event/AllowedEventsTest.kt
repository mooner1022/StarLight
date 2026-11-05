package dev.mooner.starlight.plugincore.project.event

import dev.mooner.starlight.plugincore.language.CodeGenerator
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

/** Which registered events a project's allowed-event entries cover. */
class AllowedEventsTest {

    class First : ProjectEvent() {
        override val id = "first"
        override val name = "first"
        override val functionName = "onFirst"
        override val argTypes: Array<CodeGenerator.Argument<*>> = emptyArray()
    }

    class Second : ProjectEvent() {
        override val id = "second"
        override val name = "second"
        override val functionName = "onSecond"
        override val argTypes: Array<CodeGenerator.Argument<*>> = emptyArray()
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun register() {
            ProjectEventManager.register("allowedtest.group.first", First::class)
            ProjectEventManager.register("allowedtest.group.second", Second::class)
        }
    }

    @Test
    fun `a wildcard allows every event under it`() {
        assertEquals(setOf("allowedtest.group.first", "allowedtest.group.second"),
            ProjectEventManager.filterAllowedEvents(setOf("allowedtest.*")))
    }

    @Test
    fun `an excluded entry takes its events back out`() {
        assertEquals(setOf("allowedtest.group.first"),
            ProjectEventManager.filterAllowedEvents(setOf("allowedtest.*", "!allowedtest.group.second")))
    }

    @Test
    fun `an exclusion alone allows nothing`() {
        assertEquals(emptySet<String>(), ProjectEventManager.filterAllowedEvents(setOf("!allowedtest.group.second")))
    }
}
