package dev.mooner.starlight.plugincore.widget

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun idGenerator(): () -> String {
        var next = 0
        return { "id${next++}" }
    }

    @Test
    fun migratesLegacyIdList() {
        val (layout, isMigrated) = WidgetLayout.decode(
            layout = null,
            legacyIds = """["widget_uptime_slim","widget_logs"]""",
            json = json,
            newInstanceId = idGenerator()
        )
        assertTrue(isMigrated)
        assertEquals(
            listOf(
                WidgetPlacement("id0", "widget_uptime_slim"),
                WidgetPlacement("id1", "widget_logs"),
            ),
            layout.items
        )
    }

    @Test
    fun prefersStoredLayoutOverLegacyIds() {
        val stored = WidgetLayout(items = listOf(WidgetPlacement("a", "widget_logs", 4 x 3)))
        val (layout, isMigrated) = WidgetLayout.decode(
            layout = stored.encode(json),
            legacyIds = """["widget_uptime_slim"]""",
            json = json,
        )
        assertFalse(isMigrated)
        assertEquals(stored, layout)
    }

    @Test
    fun fallsBackToLegacyIdsWhenLayoutIsCorrupt() {
        val (layout, isMigrated) = WidgetLayout.decode(
            layout = "{not json",
            legacyIds = """["widget_logs"]""",
            json = json,
            newInstanceId = idGenerator()
        )
        assertTrue(isMigrated)
        assertEquals(listOf(WidgetPlacement("id0", "widget_logs")), layout.items)
    }

    @Test
    fun returnsEmptyLayoutWhenNothingIsReadable() {
        val (layout, _) = WidgetLayout.decode(layout = null, legacyIds = "oops", json = json)
        assertTrue(layout.items.isEmpty())
    }

    @Test
    fun regeneratesDuplicateOrBlankInstanceIds() {
        val stored = WidgetLayout(
            items = listOf(
                WidgetPlacement("a", "widget_logs"),
                WidgetPlacement("a", "widget_logs"),
                WidgetPlacement("", "widget_uptime"),
            )
        )
        val (layout, _) = WidgetLayout.decode(stored.encode(json), null, json, idGenerator())
        assertEquals(listOf("a", "id0", "id1"), layout.items.map(WidgetPlacement::instanceId))
    }

    @Test
    fun encodesLegacyIdsInOrder() {
        val layout = WidgetLayout(
            items = listOf(WidgetPlacement("a", "widget_logs"), WidgetPlacement("b", "widget_uptime"))
        )
        assertEquals("""["widget_logs","widget_uptime"]""", layout.encodeLegacyIds(json))
    }
}
