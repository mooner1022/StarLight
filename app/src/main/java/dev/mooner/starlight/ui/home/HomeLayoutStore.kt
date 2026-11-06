package dev.mooner.starlight.ui.home

import dev.mooner.starlight.CA_WIDGETS
import dev.mooner.starlight.CF_IDS
import dev.mooner.starlight.CF_LAYOUT
import dev.mooner.starlight.WIDGET_DEF_STRING
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.widget.WidgetLayout
import dev.mooner.starlight.plugincore.widget.WidgetPlacement
import dev.mooner.starlight.plugincore.widget.WidgetSpan
import dev.mooner.starlight.plugincore.widget.x
import dev.mooner.starlight.ui.widget.WIDGET_UPTIME

object HomeLayoutStore {

    fun load(): WidgetLayout {
        val category = GlobalConfig.category(CA_WIDGETS)
        val (decoded, isMigrated) = WidgetLayout.decode(
            layout = category.getString(CF_LAYOUT),
            legacyIds = category.getString(CF_IDS, WIDGET_DEF_STRING),
            json = Session.json,
        )
        val layout = decoded.copy(items = decoded.items.map(::migrateRenamed))
        if (isMigrated || layout != decoded)
            save(layout)
        return layout
    }

    private fun migrateRenamed(placement: WidgetPlacement): WidgetPlacement {
        val (id, span) = RENAMED_WIDGETS[placement.widgetId]
            ?: return placement
        return placement.copy(widgetId = id, span = placement.span ?: span)
    }

    private val RENAMED_WIDGETS: Map<String, Pair<String, WidgetSpan>> = mapOf(
        "widget_uptime_slim"    to (WIDGET_UPTIME to (4 x 1)),
        "widget_uptime_default" to (WIDGET_UPTIME to (4 x 2)),
    )

    fun save(layout: WidgetLayout) {
        GlobalConfig.edit {
            category(CA_WIDGETS).apply {
                this[CF_LAYOUT] = layout.encode(Session.json)
                this[CF_IDS] = layout.encodeLegacyIds(Session.json)
            }
        }
    }
}
