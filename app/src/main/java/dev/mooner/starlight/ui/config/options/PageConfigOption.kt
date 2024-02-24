/*
 * PageConfigOption.kt created by Minki Moon(mooner1022) on 2/20/24, 4:42 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.config.options

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.setMargins
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.configdsl.*
import dev.mooner.configdsl.options.CategoryConfigOption
import dev.mooner.starlight.utils.startConfigActivity
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.properties.Delegates.notNull

class PageConfigOption(
    override val id         : String,
    override val title      : String,
    @ColorInt val textColor : Int?,
    override val description: String?,
    override val icon       : IconInfo,
    override val default    : MutableDataMapEntry = hashMapOf(),
    val content: ConfigStructure,
    private val isRootOption: Boolean = false,
): RootConfigOption<PageConfigOption.PageViewHolder, MutableDataMapEntry>() {

    override val dependency : String? = null

    private var descCache: MutableMap<String, String> = hashMapOf()

    override fun onCreateViewHolder(parent: ViewGroup): PageViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_category_nested, parent, false)
        return PageViewHolder(view)
    }

    override fun dataFromJson(jsonElement: JsonElement): MutableDataMapEntry =
        jsonElement.jsonObject.toMutableMap()

    override fun dataToJson(value: MutableDataMapEntry): JsonElement =
        JsonObject(value)

    override fun onDraw(holder: PageViewHolder, data: MutableDataMapEntry) {
        val preprocessed = preprocessData(data)

        println("PageConfigOption data ~~~~~")
        for ((id, entry) in preprocessed) {
            println("$id ~~")
            for ((key, value) in entry)
                println("\t $key: $value")
        }
        println("~~~~~~~~~~~~~~~~~~~~")

        if (!isRootOption)
            holder.layout.updateLayoutParams<RecyclerView.LayoutParams> {
                setMargins(0)
            }

        holder.title.apply {
            text = title
            setTextColor(textColor ?: context.getColor(R.color.text))
        }

        require(title.isNotBlank()) { "Title is required for page option." }
        holder.description.text = getDesc()
        icon.loadTo(holder.icon)
        holder.layout.setOnClickListener {
            holder.itemView.context.startConfigActivity(
                title = title,
                subTitle = description ?: "설정",
                struct = content,
                saved = preprocessed,
                onValueUpdated = { parentId, id, value, jsonValue ->
                    if (isRootOption) {
                        println("publishRootUpdate $parentId\$$id $value $jsonValue")
                        if (parentId == "default")
                            publishRootUpdate(id, value, jsonValue)
                        else {
                            val contentData = preprocessed[parentId]!!
                            publishRootUpdate(parentId, contentData, JsonObject(contentData))
                        }
                    } else {
                        preprocessed.computeIfAbsent(parentId) { hashMapOf() }[id] = jsonValue
                        notifyUpdated(data)
                    }
                }
            )
        }
    }

    private fun getDesc(): String {
        if (!description.isNullOrBlank())
            return description

        if (id !in descCache)
            descCache[id] = content
                .filterIsInstance<CategoryConfigOption>()
                .map { it.items.map { item -> item.title } }
                .flatten()
                .joinToString(", ")
        return descCache[id]!!
    }

    private fun preprocessData(data: MutableDataMapEntry): MutableDataMap {
        val processed: MutableDataMap = hashMapOf()
        for ((key, value) in data) {
            if (value is JsonObject)
                processed[key] = value.jsonObject.toMutableMap()
            else
                processed.computeIfAbsent("default") { hashMapOf() }[key] = value
        }
        return processed
    }

    class PageViewHolder(itemView: View): DefaultViewHolder(itemView) {
        val layout: ConstraintLayout = itemView.findViewById(R.id.layout_configNestedCategory)
    }
}

class PageConfigBuilder(
    private val isRootOption: Boolean
): ConfigBuilderBase<PageConfigOption.PageViewHolder, MutableDataMapEntry>() {

    private var structure: ConfigStructure by notNull()

    @ColorInt
    var textColor: Int? = null

    var default: MutableDataMapEntry = hashMapOf()

    @ConfigBuilderDsl
    fun structure(builder: ConfigBuilder.() -> Unit) {
        structure = ConfigBuilder()
            .apply(builder)
            .build(flush = true)
    }

    override fun build(): RootConfigOption<PageConfigOption.PageViewHolder, MutableDataMapEntry> {
        requiredField("title", title)

        return PageConfigOption(
            id           = id,
            title        = title!!,
            description  = description,
            textColor    = textColor,
            icon         = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            default      = default,
            content      = structure,
            isRootOption = isRootOption,
        )
    }
}

class SingleCategoryPageBuilder: ConfigBuilderBase<PageConfigOption.PageViewHolder, MutableDataMapEntry>() {

    private var structure: List<ConfigOption<*, *>> by notNull()

    @ColorInt
    var textColor: Int? = null

    var default: MutableDataMapEntry = hashMapOf()

    @ConfigBuilderDsl
    fun items(builder: ConfigItemBuilder.() -> Unit) {
        structure = ConfigItemBuilder()
            .apply(builder)
            .build()
    }

    override fun build(): RootConfigOption<PageConfigOption.PageViewHolder, MutableDataMapEntry> {
        requiredField("title", title)

        val actualStructure = config {
            category {
                id = "default"
                items = structure
            }
        }

        return PageConfigOption(
            id           = id,
            title        = title!!,
            description  = description,
            textColor    = textColor,
            icon         = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            default      = default,
            content      = actualStructure,
            isRootOption = true,
        )
    }
}

@ConfigBuilderDsl
fun ConfigBuilder.page(block: PageConfigBuilder.() -> Unit) {
    val category = PageConfigBuilder(isRootOption = true).apply(block)
    add(category.build())
}

@ConfigBuilderDsl
fun ConfigBuilder.singleCategoryPage(block: SingleCategoryPageBuilder.() -> Unit) {
    val category = SingleCategoryPageBuilder().apply(block)
    add(category.build())
}

@ConfigBuilderDsl
fun ConfigItemBuilder.page(block: PageConfigBuilder.() -> Unit) {
    add(PageConfigBuilder(isRootOption = false).apply(block))
}