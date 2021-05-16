package com.mooner.starlight.plugincore.core

import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class GeneralConfig(val path: File) {
    private lateinit var configs: HashMap<String, String>
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val FILE_NAME = "config_general.json"
        const val CONFIG_PROJECTS_ALIGN = "projects_align_state"
        const val CONFIG_PROJECTS_REVERSED = "projects_align_reversed"
        const val CONFIG_PROJECTS_ACTIVE_FIRST = "projects_align_active_first"
    }

    init {
        val file = File(path, FILE_NAME)
        if (!file.isFile || !file.exists()) {
            file.mkdirs()
            configs = hashMapOf()
        } else {
            Json.decodeFromString(file.readText())
        }
    }

    operator fun get(id: String): String? {
        return configs[id]
    }

    operator fun get(id: String, def: String): String {
        return get(id)?: def
    }

    operator fun set(id: String, value: String): GeneralConfig {
        configs[id] = value
        return this
    }

    fun push(): GeneralConfig {
        if (scope.isActive) {
            scope.cancel()
        }
        scope.launch {
            val str = Json.encodeToString(configs)
            File(path, FILE_NAME).writeText(str)
        }
        return this
    }
}