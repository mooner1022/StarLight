package com.mooner.starlight.plugincore.config

import com.mooner.starlight.plugincore.core.Session.json
import com.mooner.starlight.plugincore.models.TypedString
import com.mooner.starlight.plugincore.models.typed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class GlobalConfig(
    private val path: File
) {

    companion object {
        private const val FILE_NAME = "config-general.json"
        private const val DEFAULT_CATEGORY = "general"
    }

    private val configs: MutableMap<String, MutableMap<String, TypedString>> by lazy {
        if (!file.exists() || !file.isFile) {
            file.parentFile?.mkdirs()
            file.createNewFile()
            mutableMapOf()
        } else {
            val raw = file.readText()
            if (raw.isBlank())
                mutableMapOf()
            else
                json.decodeFromString(raw)
        }
    }
    private var file = File(path, FILE_NAME)
    private var isSaved: Boolean = false

    operator fun get(id: String): String? = configs[DEFAULT_CATEGORY]?.get(id)?.value

    operator fun get(id: String, def: String): String = get(id)?: def

    operator fun set(id: String, value: TypedString) {
        if (configs.containsKey(DEFAULT_CATEGORY))
            configs[DEFAULT_CATEGORY]!![id] = value
        else
            configs[DEFAULT_CATEGORY] = mutableMapOf(id to value)
    }

    operator fun set(id: String, value: String) {
        set(id, value typed "String")
    }

    fun getCategory(id: String): MutableConfigCategory {
        if (!configs.containsKey(id)) {
            configs[id] = mutableMapOf()
        }
        return MutableConfigCategory(configs[id]!!)
    }

    fun getAllConfigs(): Map<String, Map<String, TypedString>> = configs

    fun edit(block: GlobalConfig.() -> Unit) {
        this.apply(block)
        push()
    }

    fun push() {
        isSaved = true
        CoroutineScope(Dispatchers.IO).launch {
            val str = json.encodeToString(configs)
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