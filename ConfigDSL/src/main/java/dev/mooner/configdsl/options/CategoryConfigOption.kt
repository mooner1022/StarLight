/*
 * CategoryConfigOption.kt created by Minki Moon(mooner1022) on 2/17/24, 2:49 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.options

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.configdsl.*
import dev.mooner.configdsl.utils.hasFlag
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

data class CategoryConfigOption(
    override val id         : String,
    override val title      : String,
             val flags      : Int = FLAG_NONE,
             @ColorInt
             val textColor  : Int?,
    override val icon       : IconInfo,
             val items      : List<ConfigOption<*, *>>
): RootConfigOption<CategoryConfigOption.CategoryViewHolder, MutableMap<String, JsonElement>>() {

    val isDevModeOnly get() = flags hasFlag FLAG_DEV_MODE_ONLY

    override val default     : MutableMap<String, JsonElement> = hashMapOf()
    override val description : String? = null
    override val dependency  : String? = null

    override fun onCreateViewHolder(parent: ViewGroup): CategoryViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun dataFromJson(jsonElement: JsonElement): MutableMap<String, JsonElement> =
        jsonElement.jsonObject.toMutableMap()

    override fun dataToJson(value: MutableMap<String, JsonElement>): JsonElement =
        JsonObject(value)

    override fun onDraw(holder: CategoryViewHolder, data: MutableMap<String, JsonElement>) {

    }

    companion object {

        const val FLAG_NONE: Int = 0x0

        const val FLAG_DEV_MODE_ONLY: Int = 0x1
    }

    class CategoryViewHolder(itemView: View): BaseViewHolder(itemView) {

        val title   : TextView     = itemView.findViewById(R.id.title)
        val itemList: RecyclerView = itemView.findViewById(R.id.recyclerViewCategory)

        override fun setEnabled(enabled: Boolean) {}
    }
}