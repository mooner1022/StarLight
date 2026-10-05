/*
 * FileConfig.kt created by Minki Moon(mooner1022) on 4/23/23, 11:26 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config.data

import android.util.AtomicFile
import androidx.core.util.writeText
import dev.mooner.configdsl.DataMap
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.config.data.category.MutableConfigCategory
import dev.mooner.starlight.plugincore.utils.readConfigData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class FileConfig(
    file: File
): MutableConfig {

    private val file = AtomicFile(file)

    private val mutex: Mutex by lazy { Mutex(locked = false) }

    private val mData: MutableDataMap by lazy { this.file.readConfigData() }

    override fun getData(): DataMap =
        mData

    override fun getMutableData(): MutableDataMap =
        mData

    override operator fun get(id: String): MutableConfigCategory =
        category(id)

    override fun contains(id: String): Boolean =
        categoryOrNull(id) != null

    override fun category(id: String): MutableConfigCategory {
        if (id !in mData)
            mData[id] = ConcurrentHashMap()
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
            mutex.withLock {
                file.writeText(json.encodeToString(mData))
            }
        }
    }

    override fun edit(block: MutableConfig.() -> Unit) {
        this.block()
        push()
    }
}