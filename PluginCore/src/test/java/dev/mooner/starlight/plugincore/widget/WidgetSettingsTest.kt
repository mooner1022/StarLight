package dev.mooner.starlight.plugincore.widget

import dev.mooner.configdsl.options.CategoryConfigOption
import dev.mooner.configdsl.options.ChoiceConfigOption
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
    fun formAcceptsOnlyKnownChoiceKeys() {
        val form = WidgetSettingsForm(block.evaluate(), emptyMap())
        assertEquals(JsonPrimitive("Other"), form.toValue("project", JsonPrimitive("Other")))
        assertEquals(JsonPrimitive(true), form.toValue("warnings", JsonPrimitive(true)))
        assertNull(form.toValue("project", JsonPrimitive("Missing")))
        assertNull(form.toValue("unknown", JsonPrimitive("Other")))
    }

    @Test
    fun formDataKeepsStoredChoiceKey() {
        val form = WidgetSettingsForm(block.evaluate(), mapOf("project" to JsonPrimitive("Other")))
        val entries = form.data.getValue(WidgetSettingsForm.CATEGORY_ID)
        assertEquals(JsonPrimitive("Other"), entries["project"])
        assertFalse("warnings" in entries)
    }

    @Test
    fun unknownChoiceDefaultFallsBackToFirstOption() {
        val other: WidgetSettingsBlock = {
            choice("mode", "Mode", options = listOf("a" to "A", "b" to "B"), default = "gone")
        }
        val form = WidgetSettingsForm(other.evaluate(), emptyMap())
        val category = form.structure.single() as CategoryConfigOption
        assertEquals("a", (category.childOptions.single() as ChoiceConfigOption).default)
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
