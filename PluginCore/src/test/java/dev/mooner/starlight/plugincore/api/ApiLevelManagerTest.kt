package dev.mooner.starlight.plugincore.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which APIs and events a project gets from the levels it chose. */
class ApiLevelManagerTest {

    private val manager = ApiLevelManager().apply {
        register(ApiLevel("original", "Original", "StarLight", apis = setOf("Log", "Projects"),
            events = setOf("starlight.message.create"), isDefault = true))
        register(ApiLevel("legacy", "Legacy", "MessengerBot", apis = setOf("Log", "Api", "Device"),
            events = setOf("starlight.message.legacy")))
        register(ApiLevel("api2", "API2", "MessengerBot", apis = setOf("Log", "BotManager", "Device"),
            events = emptySet()))
    }

    @Test
    fun `an API is there only when a chosen level has it`() {
        assertTrue(manager.hasApi(setOf("original"), "Projects"))
        assertFalse(manager.hasApi(setOf("original"), "Api"))
        assertTrue(manager.hasApi(setOf("legacy"), "Api"))
    }

    @Test
    fun `an API several levels share is there with any of them`() {
        for (level in listOf("original", "legacy", "api2"))
            assertTrue(manager.hasApi(setOf(level), "Log"))
        assertTrue(manager.hasApi(setOf("legacy", "api2"), "Device"))
        assertFalse(manager.hasApi(setOf("original"), "Device"))
    }

    @Test
    fun `an API no level names is in every project`() {
        assertTrue(manager.hasApi(setOf("original"), "KakaoDB"))
        assertTrue(manager.hasApi(emptySet(), "KakaoDB"))
    }

    @Test
    fun `a project from before levels has everything`() {
        assertTrue(manager.hasApi(null, "Api"))
        assertTrue(manager.hasEvent(null, "starlight.message.legacy"))
    }

    @Test
    fun `events follow the levels the same way`() {
        assertTrue(manager.hasEvent(setOf("original"), "starlight.message.create"))
        assertFalse(manager.hasEvent(setOf("original"), "starlight.message.legacy"))
        assertTrue(manager.hasEvent(setOf("legacy"), "starlight.message.legacy"))
        assertTrue(manager.hasEvent(setOf("api2"), "plugin.discord.message"))
    }

    @Test
    fun `new projects start with the default levels`() {
        assertEquals(setOf("original"), manager.defaultLevelIds)
    }

    @Test
    fun `a second level with the same id is ignored`() {
        assertFalse(manager.register(ApiLevel("original", "Other", "Other", apis = setOf("Api"), events = emptySet())))
        assertFalse(manager.hasApi(setOf("original"), "Api"))
        assertEquals(3, manager.getLevels().size)
    }
}
