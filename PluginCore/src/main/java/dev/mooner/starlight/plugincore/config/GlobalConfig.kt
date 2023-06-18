/*
 * GlobalConfig.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config

import android.view.View
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.config.data.DataMap
import dev.mooner.starlight.plugincore.config.data.MutableConfig
import dev.mooner.starlight.plugincore.config.data.MutableDataMap
import dev.mooner.starlight.plugincore.config.data.category.MutableConfigCategory
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

object GlobalConfig: MutableConfig {

    private const val FILE_NAME = "config-general.json"
    private const val DEFAULT_CATEGORY = "general"

    private val mData: MutableDataMap by lazy(::loadFromFile)
    private val cachedCategories: MutableMap<String, MutableConfigCategory> = hashMapOf()
    private val file = File(getStarLightDirectory(), FILE_NAME)
    private var isSaved: Boolean = false

    override fun getData(): DataMap = mData

    override operator fun get(id: String): MutableConfigCategory =
        category(id)

    override fun contains(id: String): Boolean = categoryOrNull(id) != null

    fun getDefaultCategory(): MutableConfigCategory =
        getOrCreateCategory(DEFAULT_CATEGORY)

    override fun category(id: String): MutableConfigCategory =
        getOrCreateCategory(id)

    override fun categoryOrNull(id: String): MutableConfigCategory? =
        getCategoryOrNull(id)

    fun getDataMap(): DataMap =
        mData

    override fun edit(block: MutableConfig.() -> Unit) {
        this.apply(block)
        push()
    }

    override fun push() {
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
        EventHandler.fireEventWithScope(Events.Config.GlobalConfigUpdate())
    }

    fun onSaveConfigAdapter(parentId: String, id: String, view: View?, data: Any) {
        edit {
            category(parentId).setAny(id, data)
        }
    }

    fun invalidateCache() {
        mData.clear()
        loadFromFile().forEach(mData::put)
    }

    private fun getCategoryOrNull(id: String): MutableConfigCategory? =
        cachedCategories[id] ?: mData[id]?.let(::MutableConfigCategory)

    private fun getOrCreateCategory(id: String): MutableConfigCategory {
        return getCategoryOrNull(id) ?: let {
            mData[id] = mutableMapOf()
            MutableConfigCategory(mData[id]!!).also {
                cachedCategories[id] = it
            }
        }
    }

    private fun loadFromFile(): MutableDataMap {
        return if (!file.exists() || !file.isFile) {
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
}