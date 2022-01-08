/*
 * FileConfig.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config.data

import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.config.TypedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class FileConfig(
    private val file: File
): Config {

    private val mutex: Mutex by lazy { Mutex(locked = false) }

    val data: MutableMap<String, MutableMap<String, TypedString>> by lazy {
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

    override operator fun get(id: String): ConfigCategory = getCategory(id)

    override fun getCategory(id: String): MutableConfigCategory {
        if (id !in data)
            data[id] = mutableMapOf()
        return getCategoryOrNull(id)!!
    }

    override fun getCategoryOrNull(id: String): MutableConfigCategory? {
        val categoryData = data[id]
        return if (categoryData == null)
            null
        else
            MutableConfigCategory(categoryData)
    }

    fun push() {
        CoroutineScope(Dispatchers.IO).launch {
            val encoded = mutex.withLock {
                json.encodeToString(data)
            }
            file.writeText(encoded)
        }
    }

    fun edit(block: FileConfig.() -> Unit) {
        this.block()
        push()
    }
}