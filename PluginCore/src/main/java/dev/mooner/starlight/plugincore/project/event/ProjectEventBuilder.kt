/*
 * ProjectEventBuilder.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project.event

import dev.mooner.starlight.plugincore.plugin.PluginContext
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

typealias ProjectEventClass = KClass<out ProjectEvent>

class ProjectEventBuilder(
    private val context: PluginContext
) {

    private val events: MutableMap<String, ProjectEventClass> = hashMapOf()

    fun category(name: String, builder: CategoryBuilder.() -> Unit) {
        CategoryBuilder()
            .apply(builder)
            .build()
            .mapKeys { (k, _) -> "$name.$k" }
            .forEach(events::put)
    }

    fun build(): Map<String, ProjectEventClass> =
        events.mapKeys { (k, _) -> context.id + '.' + k }

    inner class CategoryBuilder {

        private val events: MutableMap<String, ProjectEventClass> = hashMapOf()

        fun category(name: String, builder: CategoryBuilder.() -> Unit) {
            CategoryBuilder()
                .apply(builder)
                .build()
                .mapKeys { (k, _) -> "$name.$k" }
                .forEach(events::put)
        }

        operator fun plus(event: ProjectEventClass) {
            val instance = event.createInstance()
            events[instance.id] = event
        }

        inline fun <reified T> add() where T : ProjectEvent =
            plus(T::class)

        internal fun build(): Map<String, ProjectEventClass> {
            return events
        }
    }
}