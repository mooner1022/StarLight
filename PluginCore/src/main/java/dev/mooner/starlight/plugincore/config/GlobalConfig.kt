/*
 * GlobalConfig.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config

import android.util.AtomicFile
import androidx.core.util.writeText
import dev.mooner.configdsl.DataMap
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.config.data.MutableConfig
import dev.mooner.starlight.plugincore.config.data.category.MutableConfigCategory
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.plugincore.utils.readConfigData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object GlobalConfig: MutableConfig {

    private const val FILE_NAME = "config-general.json"
    private const val DEFAULT_CATEGORY = "general"

    private val cachedCategories: MutableMap<String, MutableConfigCategory> = ConcurrentHashMap()
    private val mData           : MutableDataMap by lazy { file.readConfigData() }
    private val flushScope      = CoroutineScope(Dispatchers.IO)
    private val file            = AtomicFile(File(getStarLightDirectory(), FILE_NAME))
    private val fileAccessMutex = Mutex()

    override fun getData(): DataMap =
        mData

    override fun getMutableData(): MutableDataMap =
        mData

    override operator fun get(id: String): MutableConfigCategory =
        category(id)

    override fun contains(id: String): Boolean = categoryOrNull(id) != null

    fun getDefaultCategory(): MutableConfigCategory =
        getOrCreateCategory(DEFAULT_CATEGORY)

    override fun category(id: String): MutableConfigCategory =
        getOrCreateCategory(id)

    override fun categoryOrNull(id: String): MutableConfigCategory? =
        getCategoryOrNull(id)

    override fun edit(block: MutableConfig.() -> Unit) {
        this.apply(block)
        push()
    }

    override fun push() {
        flushScope.launch {
            fileAccessMutex.lock()
            try {
                file.writeText(json.encodeToString(mData))
            } finally {
                fileAccessMutex.unlock()
            }
            EventHandler.fireEvent(Events.Config.GlobalConfigUpdate())
        }
    }

    fun invalidateCache() {
        mData.clear()
        cachedCategories.clear()
        file.readConfigData().forEach(mData::put)
    }

    private fun getCategoryOrNull(id: String): MutableConfigCategory? =
        cachedCategories[id] ?: mData[id]?.let(::MutableConfigCategory)

    private fun getOrCreateCategory(id: String): MutableConfigCategory {
        return getCategoryOrNull(id) ?: let {
            mData[id] = ConcurrentHashMap()
            MutableConfigCategory(mData[id]!!).also {
                cachedCategories[id] = it
            }
        }
    }
}