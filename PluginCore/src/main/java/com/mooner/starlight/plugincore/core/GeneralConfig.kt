package com.mooner.starlight.plugincore.core

import com.mooner.starlight.plugincore.core.Session.json
import com.mooner.starlight.plugincore.models.TypedString
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class GeneralConfig(val path: File) {

    companion object {
        private const val FILE_NAME = "config_general.json"
        private const val DEFAULT_CATEGORY = "general"
    }

    private val configs: MutableMap<String, MutableMap<String, TypedString>> by lazy {
        if (!file.isFile || !file.exists()) {
            path.mkdirs()
            hashMapOf()
        } else {
            json.decodeFromString(file.readText())
        }
    }
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var file = File(path, FILE_NAME)

    operator fun get(id: String): String? = configs[DEFAULT_CATEGORY]?.get(id)?.value

    operator fun get(id: String, def: String): String = get(id)?: def

    operator fun set(id: String, value: String) {
        if (configs.containsKey(DEFAULT_CATEGORY))
            configs[DEFAULT_CATEGORY]!![id] = TypedString(type = "String", value = value)
        else
            configs[DEFAULT_CATEGORY] = mutableMapOf(id to TypedString(type = "String", value = value))
    }

    fun getAllConfigs(): Map<String, Map<String, TypedString>> = configs

    fun push() {
        if (scope.isActive) {
            scope.cancel()
        }

        scope.launch {
            val str = synchronized(configs) { json.encodeToString(configs) }
            with(file) {
                if (!exists()) {
                    mkdirs()
                }
                if (!isFile) {
                    deleteRecursively()
                }
                writeText(str)
            }
        }

        /*
        scope.launch {
            val str = Json.encodeToString(configs)
            println("saved: $str")
            File(path, FILE_NAME).writeText(str)
        }
        */
    }
}