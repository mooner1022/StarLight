package dev.mooner.starlight.plugincore.project.event

import dev.mooner.starlight.plugincore.language.CodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checking and unchecking events edits a project's allowed-event entries as little as it can. */
class AllowedEventEntriesTest {

    class Registered : ProjectEvent() {
        override val id = "first"
        override val name = "first"
        override val functionName = "onFirst"
        override val argTypes: Array<CodeGenerator.Argument<*>> = emptyArray()
    }

    private val registered = setOf(
        "starlight.message.create", "starlight.message.legacy", "starlight.project.compile",
        "starlight.api2.message", "starlight.api2.command",
        "web.server.request",
    )

    /** Wildcards as [ProjectEventManager] reads them, over [registered]. */
    private fun resolve(entry: String): Set<String> = when {
        entry == "*" -> registered
        entry.endsWith(".*") -> registered.filterTo(hashSetOf()) { it.startsWith(entry.dropLast(1)) }
        entry in registered -> setOf(entry)
        else -> emptySet()
    }

    private fun entries(vararg entries: String) =
        AllowedEventEntries(entries.toList(), ::resolve)

    @Test
    fun `checking an event adds its ID`() {
        val entries = entries()
        entries.setAllowed("starlight.message.create", true)
        assertEquals(listOf("starlight.message.create"), entries.entries)
    }

    @Test
    fun `unchecking an event added by ID removes it without an exclusion`() {
        val entries = entries("starlight.message.create", "starlight.project.compile")
        entries.setAllowed("starlight.message.create", false)
        assertEquals(listOf("starlight.project.compile"), entries.entries)
    }

    @Test
    fun `unchecking an event a wildcard covers excludes it and checking it again takes that back`() {
        val entries = entries("starlight.*")
        entries.setAllowed("starlight.message.legacy", false)
        assertEquals(listOf("starlight.*", "!starlight.message.legacy"), entries.entries)
        assertFalse(entries.isAllowed("starlight.message.legacy"))

        entries.setAllowed("starlight.message.legacy", true)
        assertEquals(listOf("starlight.*"), entries.entries)
    }

    @Test
    fun `checking an event a wildcard exclusion takes out keeps the rest of it out`() {
        val entries = entries("starlight.*", "!starlight.api2.*")
        entries.setAllowed("starlight.api2.message", true)
        assertEquals(listOf("starlight.*", "!starlight.api2.command"), entries.entries)
        assertEquals(registered - "starlight.api2.command" - "web.server.request", entries.allowed)
    }

    @Test
    fun `entries matching no registered event stay through changes`() {
        val entries = entries("kakaodb.*", "!kakaodb.message.read", "starlight.mesage.create")
        entries.setAllowed("starlight.message.create", true)
        entries.setAllowed("starlight.message.create", false)
        assertEquals(listOf("kakaodb.*", "!kakaodb.message.read", "starlight.mesage.create"), entries.entries)
        assertEquals(listOf("kakaodb.*", "!kakaodb.message.read", "starlight.mesage.create"), entries.unknown)
    }

    @Test
    fun `receiving later events turns the source's checked events into a wildcard`() {
        val entries = entries("starlight.message.create", "web.server.request")
        entries.setAutomatic("starlight", true)
        val before = setOf("starlight.message.create", "web.server.request")
        assertEquals(before, entries.allowed)
        assertTrue(entries.isAutomatic("starlight"))
        assertEquals("web.server.request", entries.entries.first())
        assertTrue("starlight.*" in entries.entries)
    }

    @Test
    fun `no longer receiving later events lists the checked events by ID`() {
        val entries = entries("starlight.*", "!starlight.message.legacy", "!starlight.api2.*")
        entries.setAutomatic("starlight", false)
        assertEquals(listOf("starlight.message.create", "starlight.project.compile"), entries.entries)
        assertFalse(entries.isAutomatic("starlight"))
    }

    @Test
    fun `turning a wildcard off keeps out what a narrower wildcard covers`() {
        val entries = entries("starlight.*", "starlight.message.*", "!starlight.message.legacy")
        val before = entries.allowed
        entries.setAutomatic("starlight", false)
        assertEquals(before, entries.allowed)
        assertFalse(entries.isAutomatic("starlight"))
    }

    @Test
    fun `everything stays automatic while an entry allows every source`() {
        val entries = entries("*")
        assertTrue(entries.isAutomatic("web"))
        assertFalse(entries.canSetAutomatic("web"))
        entries.setAutomatic("web", false)
        assertEquals(listOf("*"), entries.entries)
    }

    @Test
    fun `checking several events at once`() {
        val entries = entries("starlight.*", "!starlight.api2.*")
        entries.setAllowed(listOf("starlight.api2.message", "starlight.api2.command"), true)
        assertEquals(listOf("starlight.*"), entries.entries)
    }

    @Test
    fun `the registered events are read by default`() {
        ProjectEventManager.register("entrytest.group.first", Registered::class)
        val entries = AllowedEventEntries(listOf("entrytest.*"))
        assertEquals(setOf("entrytest.group.first"), entries.allowed)
        assertEquals(emptyList<String>(), entries.unknown)
    }
}
