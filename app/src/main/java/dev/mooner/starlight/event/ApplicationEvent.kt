/*
 * ApplicationEvent.kt created by Minki Moon(mooner1022) on 22. 12. 26. 오후 4:37
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.event

import androidx.lifecycle.LifecycleOwner
import dev.mooner.starlight.plugincore.event.Event
import dev.mooner.starlight.plugincore.event.eventHandlerScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement

sealed class ApplicationEvent {

    sealed class Session {

        data class StageUpdate(
            val value: String?,
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope
    }

    sealed class ConfigActivity {

        data class Destroy(
            val uuid: String,
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope, ConfigActivity()

        data class Update(
            val uuid: String,
            val data: UpdatedData?,
            val coroutineScope: CoroutineScope = eventHandlerScope()
        ): Event, CoroutineScope by coroutineScope, ConfigActivity() {

            data class UpdatedData(
                val parentId: String,
                val id: String,
                val data: Any,
                val jsonData: JsonElement
            )
        }
    }

    sealed class Lifecycle {

        data class Update(
            val source: LifecycleOwner,
            val event: androidx.lifecycle.Lifecycle.Event,
            val coroutineScope: CoroutineScope = eventHandlerScope(),
        ): Event, CoroutineScope by coroutineScope, Lifecycle()
    }
}