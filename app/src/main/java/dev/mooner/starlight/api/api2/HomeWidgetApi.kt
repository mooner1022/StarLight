package dev.mooner.starlight.api.api2

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.stats.ScriptWidgetEntries

class HomeWidgetApi: Api<HomeWidgetApi.HomeWidget>() {

    class HomeWidget(
        private val project: Project,
    ) {

        fun set(key: String, value: Any?) =
            set(key, key, value)

        fun set(key: String, label: String, value: Any?) =
            ScriptWidgetEntries.set(project, key, label, formatValue(value))

        fun remove(key: String) =
            ScriptWidgetEntries.remove(project, key)

        fun clear() =
            ScriptWidgetEntries.clear(project)
    }

    override val name: String = "HomeWidget"

    override val objects: List<ApiObject> =
        getApiObjects<HomeWidget>()

    override val instanceClass: Class<HomeWidget> =
        HomeWidget::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any =
        HomeWidget(project)
}

private val LONG_RANGE = Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()

private fun formatValue(value: Any?): String =
    when (value) {
        null      -> "-"
        is Double -> if (value % 1.0 == 0.0 && value in LONG_RANGE) value.toLong().toString() else value.toString()
        is Float  -> if (value % 1f == 0f && value.toDouble() in LONG_RANGE) value.toLong().toString() else value.toString()
        else      -> value.toString()
    }
