/*
 * GlobalConfig.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config

import android.view.View
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.config.category.ConfigCategory
import dev.mooner.starlight.plugincore.config.category.MutableConfigCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class GlobalConfig(
    path: File
): Config {

    private val mData: MutableMap<String, MutableMap<String, TypedString>> by lazy {
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

    override fun getData(): ConfigData = mData

    override operator fun get(id: String): ConfigCategory =
        category(id)

    override fun contains(id: String): Boolean = categoryOrNull(id) != null

    fun getDefaultCategory(): MutableConfigCategory =
        getOrCreateCategory(DEFAULT_CATEGORY)

    override fun category(id: String): MutableConfigCategory =
        getOrCreateCategory(id)

    override fun categoryOrNull(id: String): MutableConfigCategory? =
        getCategoryOrNull(id)

    fun getAllConfigs(): Map<String, Map<String, TypedString>> =
        mData

    fun edit(block: GlobalConfig.() -> Unit) {
        this.apply(block)
        push()
    }

    fun push() {
        isSaved = true
        CoroutineScope(Dispatchers.IO).launch {
            val str = json.encodeToString(mData)
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
            category(parentId).setAny(id, data)
        }
    }

    private fun getCategoryOrNull(id: String): MutableConfigCategory? =
        cachedCategories[id] ?: mData[id]?.let { MutableConfigCategory(it) }

    private fun getOrCreateCategory(id: String): MutableConfigCategory {
        return getCategoryOrNull(id) ?: let {
            mData[id] = mutableMapOf()
            MutableConfigCategory(mData[id]!!).also {
                cachedCategories[id] = it
            }
        }
    }

    companion object {
        private const val FILE_NAME = "config-general.json"
        private const val DEFAULT_CATEGORY = "general"
    }
}