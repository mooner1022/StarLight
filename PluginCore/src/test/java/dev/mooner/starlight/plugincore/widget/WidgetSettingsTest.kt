package dev.mooner.starlight.plugincore.widget

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSettingsTest {

    private val block: WidgetSettingsBlock = {
        toggle("warnings", "Show warnings", default = true)
        choice("project", "Project", options = listOf("" to "All", "Bot" to "Bot", "Other" to "Other"))
    }

    @Test
    fun readsDefaultsWhenNothingIsStored() {
        val settings = block.evaluate().toSettings(emptyMap())
        assertTrue(settings.boolean("warnings"))
        assertEquals("", settings.string("project"))
        assertNull(settings.string("unknown"))
    }

    @Test
    fun storedValuesOverrideDefaults() {
        val settings = block.evaluate().toSettings(
            mapOf("warnings" to JsonPrimitive(false), "project" to JsonPrimitive("Bot"))
        )
        assertFalse(settings.boolean("warnings"))
        assertEquals("Bot", settings.string("project"))
    }

    @Test
    fun formMapsChoiceIndexToOptionValue() {
        val form = WidgetSettingsForm(block.evaluate(), emptyMap())
        assertEquals(JsonPrimitive("Other"), form.toValue("project", JsonPrimitive(2)))
        assertEquals(JsonPrimitive(true), form.toValue("warnings", JsonPrimitive(true)))
        assertNull(form.toValue("project", JsonPrimitive(9)))
        assertNull(form.toValue("unknown", JsonPrimitive(1)))
    }

    @Test
    fun formDataMapsStoredChoiceValueToIndex() {
        val form = WidgetSettingsForm(block.evaluate(), mapOf("project" to JsonPrimitive("Other")))
        val entries = form.data.getValue(WidgetSettingsForm.CATEGORY_ID)
        assertEquals(JsonPrimitive(2), entries["project"])
        assertFalse("warnings" in entries)
    }

    @Test
    fun removedChoiceFallsBackToFirstOption() {
        val form = WidgetSettingsForm(block.evaluate(), mapOf("project" to JsonPrimitive("Deleted")))
        assertEquals(JsonPrimitive(0), form.data.getValue(WidgetSettingsForm.CATEGORY_ID)["project"])
    }

    @Test
    fun placementSettingsSurviveLayoutRoundTrip() {
        val json = Json { ignoreUnknownKeys = true }
        val layout = WidgetLayout(
            items = listOf(WidgetPlacement("a", "widget_script", settings = mapOf("project" to JsonPrimitive("Bot"))))
        )
        val (decoded, _) = WidgetLayout.decode(layout.encode(json), null, json)
        assertEquals(layout, decoded)
    }
}
