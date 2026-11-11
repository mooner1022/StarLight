package dev.mooner.starlight.utils

import dev.mooner.configdsl.MutableDataMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigDraftsTest {

    private fun stored(): MutableDataMap = hashMapOf(
        "general" to hashMapOf("shutdown_on_error" to JsonPrimitive(true), "name" to JsonPrimitive("a")),
    )

    @Test
    fun changesComeBackAsTheyWere() {
        val changed: MutableDataMap = hashMapOf(
            "general" to hashMapOf("name" to JsonPrimitive("b")),
            "api" to hashMapOf("levels" to JsonArray(listOf(JsonPrimitive("starlight"), JsonPrimitive("msgbot.api2")))),
        )

        assertEquals(changed, ConfigDrafts.decode(ConfigDrafts.encode(changed)))
    }

    @Test
    fun missingOrBrokenTextIsNoChanges() {
        assertTrue(ConfigDrafts.decode(null).isEmpty())
        assertTrue(ConfigDrafts.decode("").isEmpty())
        assertTrue(ConfigDrafts.decode("{not json").isEmpty())
    }

    @Test
    fun editingTheCopyLeavesTheStoredValuesAlone() {
        val stored = stored()
        val copy = ConfigDrafts.editableCopy(stored)

        copy["general"]!!["name"] = JsonPrimitive("changed")
        copy["new"] = hashMapOf("x" to JsonPrimitive(1))

        assertEquals(JsonPrimitive("a"), stored["general"]!!["name"])
        assertTrue("new" !in stored)
    }

    @Test
    fun overlayPutsTheChangesOverTheStoredValuesAndKeepsTheRest() {
        val data = ConfigDrafts.editableCopy(stored())
        val changed: MutableDataMap = hashMapOf(
            "general" to hashMapOf("name" to JsonPrimitive("b")),
            "beta" to hashMapOf("flag" to JsonPrimitive(true)),
        )

        ConfigDrafts.overlay(data, changed)

        assertEquals(JsonPrimitive("b"), data["general"]!!["name"])
        assertEquals(JsonPrimitive(true), data["general"]!!["shutdown_on_error"])
        assertEquals(JsonPrimitive(true), data["beta"]!!["flag"])
    }
}
