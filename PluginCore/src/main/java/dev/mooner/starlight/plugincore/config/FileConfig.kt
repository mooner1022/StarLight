/*
 * FileConfig.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config

import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.config.category.MutableConfigCategory
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
): MutableConfig {

    private val mutex: Mutex by lazy { Mutex(locked = false) }

    private val mData: MutableMap<String, MutableMap<String, TypedString>> by lazy {
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

    override fun getData(): ConfigData = mData

    override operator fun get(id: String): MutableConfigCategory =
        category(id)

    override fun contains(id: String): Boolean =
        categoryOrNull(id) != null

    override fun category(id: String): MutableConfigCategory {
        if (id !in mData)
            mData[id] = mutableMapOf()
        return categoryOrNull(id)!!
    }

    override fun categoryOrNull(id: String): MutableConfigCategory? {
        val categoryData = mData[id]
        return if (categoryData == null)
            null
        else
            MutableConfigCategory(categoryData)
    }

    override fun push() {
        CoroutineScope(Dispatchers.IO).launch {
            val encoded = mutex.withLock {
                json.encodeToString(mData)
            }
            file.writeText(encoded)
        }
    }

    override fun edit(block: MutableConfig.() -> Unit) {
        this.block()
        push()
    }
}