package dev.mooner.starlight.ui.home

import dev.mooner.starlight.CA_WIDGETS
import dev.mooner.starlight.CF_IDS
import dev.mooner.starlight.CF_LAYOUT
import dev.mooner.starlight.WIDGET_DEF_STRING
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.widget.WidgetLayout

object HomeLayoutStore {

    fun load(): WidgetLayout {
        val category = GlobalConfig.category(CA_WIDGETS)
        val (layout, isMigrated) = WidgetLayout.decode(
            layout = category.getString(CF_LAYOUT),
            legacyIds = category.getString(CF_IDS, WIDGET_DEF_STRING),
            json = Session.json,
        )
        if (isMigrated)
            save(layout)
        return layout
    }

    fun save(layout: WidgetLayout) {
        GlobalConfig.edit {
            category(CA_WIDGETS).apply {
                this[CF_LAYOUT] = layout.encode(Session.json)
                this[CF_IDS] = layout.encodeLegacyIds(Session.json)
            }
        }
    }
}
