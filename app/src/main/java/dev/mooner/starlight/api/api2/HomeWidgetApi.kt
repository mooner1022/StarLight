package dev.mooner.starlight.api.api2

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.stats.ScriptWidgetEntries

class HomeWidgetApi: Api<HomeWidgetApi.HomeWidget>() {

    /**
     * Values a script shows on StarLight's Home tab, in the Script values widget; a StarLight original global.
     *
     * The user has to add the Script values widget (스크립트 값 on a Korean phone) to the Home tab to see them. Each value has a key, unique within
     * the project, and a label shown next to it. The widget lists the most recently set first, as many as fit, and
     * puts the project's name in front when values of several projects show.
     *
     * Values are kept in memory only: they stay when the project compiles again or is turned off, go when it is
     * deleted, and are gone after StarLight restarts.
     */
    class HomeWidget(
        private val project: Project,
    ) {

        /**
         * Shows [value] under [key], with the key as its label.
         *
         * Setting a key again replaces its value and moves it to the top.
         *
         * @param key The value's key, which is also its label.
         * @param value What to show: `null` shows as `-`, a whole number without a decimal point, anything else as
         * text.
         */
        fun set(key: String, value: Any?) =
            set(key, key, value)

        /**
         * Shows [value] under [key], labeled [label].
         *
         * Setting a key again replaces its label and value and moves it to the top.
         *
         * @param key The value's key, unique within the project.
         * @param label The text shown next to the value.
         * @param value What to show: `null` shows as `-`, a whole number without a decimal point, anything else as
         * text.
         */
        fun set(key: String, label: String, value: Any?) =
            ScriptWidgetEntries.set(project, key, label, formatValue(value))

        /**
         * Removes the value shown under [key].
         *
         * @param key The value's key; nothing happens if it isn't shown.
         */
        fun remove(key: String) =
            ScriptWidgetEntries.remove(project, key)

        /** Removes every value this project shows. */
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
