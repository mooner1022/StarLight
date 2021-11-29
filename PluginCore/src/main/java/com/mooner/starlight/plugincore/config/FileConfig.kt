package com.mooner.starlight.plugincore.config

import com.mooner.starlight.plugincore.core.Session.json
import com.mooner.starlight.plugincore.models.TypedString
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
        } else
            json.decodeFromString(file.readText())
    }

    override operator fun get(id: String): ConfigCategory = getCategory(id)

    override fun getCategory(id: String): ConfigCategory {
        if (id !in data)
            data[id] = mutableMapOf()
        return getCategoryOrNull(id)!!
    }

    override fun getCategoryOrNull(id: String): ConfigCategory? {
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