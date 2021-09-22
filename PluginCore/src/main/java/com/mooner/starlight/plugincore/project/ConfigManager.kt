package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.core.Session.Companion.json
import com.mooner.starlight.plugincore.models.TypedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class ConfigManager(
    private val configFile: File
) {

    init {
        with(configFile) {
            if (!exists() || !isFile) {
                parentFile?.mkdirs()
                createNewFile()
            }
        }
        sync()
    }

    private var isUpdated: Boolean = false
    private var config: ConcurrentMap<String, MutableMap<String, TypedString>> = ConcurrentHashMap()

    fun update(updated: ConcurrentMap<String, MutableMap<String, TypedString>>) {
        this.config = updated
        isUpdated = true
    }

    fun update(updated: Map<String, MutableMap<String, TypedString>>) = update(ConcurrentHashMap(updated))

    fun getAllConfig(): MutableMap<String, MutableMap<String, TypedString>> = config

    fun getConfigForId(id: String): MutableMap<String, TypedString>? = config[id]

    fun sync() {
        CoroutineScope(Dispatchers.IO).launch {
            if (!isUpdated) {
                val raw = configFile.readText()
                config = if (raw.isNotBlank()) json.decodeFromString(raw) else ConcurrentHashMap()
            } else {
                val encoded = json.encodeToString(config)
                configFile.writeText(encoded)
            }
        }
    }
}