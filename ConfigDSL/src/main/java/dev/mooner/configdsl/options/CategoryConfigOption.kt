/*
 * CategoryConfigOption.kt created by Minki Moon(mooner1022) on 2/17/24, 2:49 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.options

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import dev.mooner.configdsl.*
import dev.mooner.configdsl.utils.hasFlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

class CategoryConfigOption(
    override val id          : String,
    override val title       : String,
             val flags       : Int = FLAG_NONE,
             @ColorInt
             val textColor   : Int?,
    override val childOptions: List<ConfigOption<*, *>>
): RootConfigOption<CategoryConfigOption.CategoryViewHolder, MutableMap<String, JsonElement>>() {

    val isDevModeOnly get() = flags hasFlag FLAG_DEV_MODE_ONLY

    override val icon        : IconInfo = IconInfo.none()
    override val default     : MutableMap<String, JsonElement> = hashMapOf()
    override val description : String? = null
    override val dependency  : String? = null

    private val childEvents = MutableSharedFlow<EventData>(extraBufferCapacity = Channel.UNLIMITED)
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var eventJob: Job? = null

    private val values: MutableMap<String, JsonElement> = hashMapOf()
    private val dependencyValues: MutableMap<String, Boolean> = hashMapOf()
    private var boundData: Map<String, JsonElement>? = null
    private var boundHolder: CategoryViewHolder? = null

    init {
        childOptions.forEach { it.init(childEvents) }
    }

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
        if (eventJob?.isActive != true)
            eventJob = childEvents.onEach(::onChildEvent).launchIn(eventScope)
        if (data !== boundData) {
            boundData = data
            values.clear()
            values.putAll(data)
            dependencyValues.clear()
        }

        title.nullIfBlank()?.let {
            holder.title.apply {
                text = it
                visibility = View.VISIBLE
                setTextColor(textColor ?: context.getColor(R.color.text))
            }
        } ?: let {
            holder.title.visibility = View.INVISIBLE
        }

        if (holder.boundOption !== this) {
            holder.boundOption?.unbind(holder)
            holder.inflateRows(childOptions)
            holder.boundOption = this
        }
        boundHolder = holder
        childOptions.forEachIndexed { index, option -> drawRow(option, holder.rows[index]) }
        applyDependencies(holder)
    }

    override fun onDestroyed() {
        eventJob?.cancel()
        eventJob = null
        boundHolder = null
        boundData = null
        for (option in childOptions)
            option.runCatching(ConfigOption<*, *>::onDestroyed)
    }

    private fun unbind(holder: CategoryViewHolder) {
        if (boundHolder === holder)
            boundHolder = null
    }

    private fun onChildEvent(event: EventData) {
        when (event) {
            is ValueUpdateData -> {
                values[event.provider] = event.jsonData
                tryEmitData(RootUpdateData(
                    rootId   = id,
                    provider = event.provider,
                    data     = event.data,
                    jsonData = event.jsonData
                ))
            }
            is DependencyUpdateData -> {
                dependencyValues[event.provider] = event.data
                boundHolder?.let(::applyDependencies)
            }
            is RedrawRequestData -> {
                val holder = boundHolder ?: return
                val index = childOptions.indexOfFirst { it.id == event.provider }
                if (index != -1)
                    drawRow(childOptions[index], holder.rows[index])
            }
        }
    }

    private fun drawRow(option: ConfigOption<*, *>, holder: BaseViewHolder) {
        @Suppress("UNCHECKED_CAST")
        option as ConfigOption<BaseViewHolder, Any>

        if (holder is DefaultViewHolder) {
            holder.title.text = option.title
            holder.description.text = option.description
            holder.description.visibility = if (option.description.isNullOrEmpty()) View.GONE else View.VISIBLE
            option.icon.loadTo(holder.icon)
        }

        val value = values[option.id]
            ?.let { json -> runCatching { option.dataFromJson(json) }.getOrNull() }
            ?: option.default
        option.onDraw(holder, value)
    }

    private fun applyDependencies(holder: CategoryViewHolder) {
        childOptions.forEachIndexed { index, option ->
            val dependency = option.dependency ?: return@forEachIndexed
            val isInverted = dependency.startsWith('!')
            val dependencyId = dependency.removePrefix("!")
            val isEnabled = dependencyValueOf(dependencyId) ?: return@forEachIndexed
            val enabled = isEnabled xor isInverted
            holder.rows[index].setEnabled(enabled)
            option.setEnabled(enabled)
        }
    }

    private fun dependencyValueOf(id: String): Boolean? =
        dependencyValues[id]
            ?: (values[id] as? JsonPrimitive)?.booleanOrNull
            ?: (childOptions.find { it.id == id } as? ToggleConfigOption)?.default

    private fun String.nullIfBlank(): String? =
        this.ifBlank { null }

    companion object {

        const val FLAG_NONE: Int = 0x0

        const val FLAG_DEV_MODE_ONLY: Int = 0x1
    }

    class CategoryViewHolder(itemView: View): BaseViewHolder(itemView) {

        val title    : TextView     = itemView.findViewById(R.id.title)
        val container: LinearLayout = itemView.findViewById(R.id.itemContainer)

        internal var boundOption: CategoryConfigOption? = null
        internal val rows: MutableList<BaseViewHolder> = arrayListOf()

        internal fun inflateRows(options: List<ConfigOption<*, *>>) {
            container.removeAllViews()
            rows.clear()
            val dividerInset = container.resources.getDimensionPixelSize(R.dimen.configdsl_divider_inset)
            options.forEachIndexed { index, option ->
                if (index > 0)
                    container.addView(View(container.context).apply {
                        setBackgroundResource(R.color.configdsl_divider)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                            marginStart = dividerInset
                        }
                    })
                val row = option.onCreateViewHolder(container)
                container.addView(row.itemView)
                rows += row
            }
        }

        override fun setEnabled(enabled: Boolean) {}
    }
}
