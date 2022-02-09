/*
 * GlobalConfig.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config.data

import android.view.View
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.config.TypedString
import dev.mooner.starlight.plugincore.config.typed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class GlobalConfig(
    path: File
) {

    companion object {
        private const val FILE_NAME = "config-general.json"
        private const val DEFAULT_CATEGORY = "general"
    }

    private val configs: MutableMap<String, MutableMap<String, TypedString>> by lazy {
        if (!file.exists() || !file.isFile) {
            file.parentFile?.mkdirs()
            file.createNewFile()
            hashMapOf()
        } else {
            val raw = file.readText()
            if (raw.isBlank())
                hashMapOf()
            else
                json.decodeFromString(raw)
        }
    }
    private val cachedCategories: MutableMap<String, MutableConfigCategory> = hashMapOf()
    private var file = File(path, FILE_NAME)
    private var isSaved: Boolean = false

    @Deprecated(
        message = "Retained for legacy compatability. Use getCategory(id).",
        replaceWith = ReplaceWith("this.getCategory(id)")
    )
    operator fun get(id: String): String? = configs[DEFAULT_CATEGORY]?.get(id)?.value

    @Deprecated(
        message = "Retained for legacy compatability. Use getCategory(id).",
        replaceWith = ReplaceWith("this.getCategory(id)")
    )
    operator fun get(id: String, def: String): String = get(id)?: def

    operator fun set(id: String, value: TypedString) {
        if (DEFAULT_CATEGORY in configs)
            configs[DEFAULT_CATEGORY]!![id] = value
        else
            configs[DEFAULT_CATEGORY] = mutableMapOf(id to value)
    }

    operator fun set(id: String, value: String) {
        set(id, value typed "String")
    }

    fun getDefaultCategory(): MutableConfigCategory = getOrCreateCategory(DEFAULT_CATEGORY)

    fun getCategory(id: String): MutableConfigCategory = getOrCreateCategory(id)

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

    fun onSaveConfigAdapter(parentId: String, id: String, view: View?, data: Any) {
        edit {
            getCategory(parentId).setAny(id, data)
        }
    }

    private fun getOrCreateCategory(id: String): MutableConfigCategory {
        return when (id) {
            in cachedCategories -> cachedCategories[id]!!
            in configs -> MutableConfigCategory(configs[id]!!).also {
                cachedCategories[id] = it
            }
            else -> {
                configs[id] = mutableMapOf()
                MutableConfigCategory(configs[id]!!).also {
                    cachedCategories[id] = it
                }
            }
        }
    }
}