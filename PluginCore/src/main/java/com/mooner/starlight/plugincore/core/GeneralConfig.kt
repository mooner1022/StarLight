package com.mooner.starlight.plugincore.core

import com.mooner.starlight.plugincore.core.Session.json
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class GeneralConfig(val path: File) {

    companion object {
        private const val FILE_NAME = "config_general.json"

        const val CONFIG_ALL_PROJECTS_POWER = "all_project_power"

        //const val CONFIG_THEME_CURRENT = "theme_current"
    }

    private var configs: MutableMap<String, String>
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var file = File(path, FILE_NAME)

    init {
        configs = if (!file.isFile || !file.exists()) {
            path.mkdirs()
            hashMapOf()
        } else {
            json.decodeFromString(file.readText())
        }
    }

    operator fun get(id: String): String? = configs[id]

    operator fun get(id: String, def: String): String = get(id)?: def

    operator fun set(id: String, value: String) {
        configs[id] = value
    }

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