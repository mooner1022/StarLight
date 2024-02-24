/*
 * ConfigUtils.kt created by Minki Moon(mooner1022) on 2/18/24, 1:50 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.utils

import dev.mooner.configdsl.DataMap
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.data.MutableConfig
import dev.mooner.starlight.plugincore.config.data.MutableLegacyDataMap
import dev.mooner.starlight.plugincore.config.data.PrimitiveTypedString
import kotlinx.serialization.json.*

fun PrimitiveTypedString.toJsonElement(json: Json = Session.json): JsonElement {
    return when(type) {
        "String"  -> {
            if (value.startsWith("[{")) {
                json.decodeFromString<List<Map<String, PrimitiveTypedString>>>(value).let { legacy ->
                    val nList: MutableList<JsonElement> = arrayListOf()
                    for (entry in legacy) {
                        val map = entry.mapValues { (_, value) -> value.toJsonElement() }
                        nList += JsonObject(map)
                    }
                    JsonArray(nList)
                }
            } else
                JsonPrimitive(value)
        }
        "Boolean" -> JsonPrimitive(castAs<Boolean>())
        "Float"   -> JsonPrimitive(castAs<Float>())
        "Int"     -> JsonPrimitive(castAs<Int>())
        "Long"    -> JsonPrimitive(castAs<Long>())
        "Double"  -> JsonPrimitive(castAs<Double>())
        else -> JsonPrimitive(value)
    }
}

private fun transformLegacyData(legacyDataMap: MutableLegacyDataMap): MutableDataMap {
    val transformed: MutableDataMap = hashMapOf()
    for ((categoryId, categoryData) in legacyDataMap) {
        transformed[categoryId] = hashMapOf()
        val catTData = transformed[categoryId]!!
        for ((key, value) in categoryData) {
            val tf = value.toJsonElement()
            catTData[key] = tf
        }
    }
    return transformed
}

fun Json.decodeLegacyData(string: String): MutableDataMap {
    val legacyData = decodeFromString<MutableLegacyDataMap>(string)
    return transformLegacyData(legacyData)
}

fun MutableConfig.onSaveConfigAdapter(parentId: String, id: String, data: Any, jsonData: JsonElement) {
    edit {
        category(parentId).setRaw(id, jsonData)
    }
}

fun DataMap.dumpAllData() {
    for ((catId, entry) in this) {
        println("$catId:\\")
        for ((key, value) in entry)
            println("\t $key: $value")
    }
}