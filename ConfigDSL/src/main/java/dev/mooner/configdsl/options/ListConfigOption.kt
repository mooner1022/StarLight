/*
 * ListConfigOption.kt created by Minki Moon(mooner1022) on 2/17/24, 2:40 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.options

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.divider.MaterialDividerItemDecoration
import dev.mooner.configdsl.*
import dev.mooner.configdsl.adapters.ConfigAdapter
import dev.mooner.configdsl.adapters.ListRecyclerAdapter
import dev.mooner.configdsl.utils.setCommonAttrs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.properties.Delegates.notNull

typealias OnInflateBlock     = (view: View) -> Unit
typealias OnDrawBlock        = (view: View, data: JsonObject) -> Unit
typealias DataTransformBlock = (data: MutableMap<String, JsonElement>) -> MutableMap<String, JsonElement>

data class ListConfigOption(
    override val id           : String,
    override val title        : String,
    override val description  : String? = null,
    val onInflate             : OnInflateBlock,
    val onDraw                : OnDrawBlock,
    val transformData         : DataTransformBlock,
    val structure             : List<ConfigOption<*, *>>,
    override val default      : List<JsonObject>,
    override val icon         : IconInfo,
    override val dependency   : String? = null,
): ConfigOption<ListConfigOption.ListViewHolder, List<JsonObject>>() {

    private var itemTouchCallback: ListRecyclerTouchCallback? = null

    override fun onCreateViewHolder(parent: ViewGroup): ListViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_list, parent, false)
        return ListViewHolder(view)
    }

    override fun dataToJson(value: List<JsonObject>): JsonElement =
        JsonArray(value)

    override fun dataFromJson(jsonElement: JsonElement): List<JsonObject> {
        return if (jsonElement is JsonPrimitive) { // Legacy TypedString support
            val nList: MutableList<JsonObject> = arrayListOf()
            val dataList: List<Map<String, PrimitiveTypedString>> = Json.decodeFromString(jsonElement.content)
            for (entry in dataList) {
                val nObj: MutableMap<String, JsonElement> = hashMapOf()
                for ((key, value) in entry) {
                    nObj[key] = value.toJsonElement()
                }
                nList += JsonObject(nObj)
            }
            nList
        } else
            jsonElement.jsonArray.map { it.jsonObject }
    }

    override fun onDraw(holder: ListViewHolder, data: List<JsonObject>) {
        val recyclerAdapter = ListRecyclerAdapter(onDraw, onInflate, structure, data.toMutableList()) { cfgList ->
            notifyUpdated(cfgList)
        }
        val itemTouchCallback = createItemTouchAdapter(recyclerAdapter)
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(holder.recyclerViewList)
        this.itemTouchCallback = itemTouchCallback

        holder.recyclerViewList.apply {
            //itemAnimator = FadeInUpAnimator()
            layoutManager = LinearLayoutManager(context)
            adapter = recyclerAdapter
            addItemDecoration(MaterialDividerItemDecoration(context, MaterialDividerItemDecoration.VERTICAL).apply {
                isLastItemDecorated = false
            })
        }
        recyclerAdapter.notifyItemRangeInserted(0, data.size)

        holder.buttonAddList.setOnClickListener {
            val configData: MutableMap<String, JsonElement> = mutableMapOf()
            MaterialDialog(holder.itemView.context, BottomSheet(LayoutMode.WRAP_CONTENT))
                .show {
                    title(R.string.add)
                    setCommonAttrs()
                    customView(R.layout.dialog_logs)

                    val recycler: RecyclerView = findViewById(R.id.rvLog)
                    val configAdapter = ConfigAdapter.Builder(view.context) {
                        bind(recycler)
                        onValueUpdated { _, id, _, jsonData ->
                            configData[id] = jsonData
                        }
                        structure {
                            config {
                                category {
                                    id = "list_structure"
                                    items = structure
                                }
                            }
                        }
                    }.build()

                    onDismiss {
                        configAdapter.destroy()
                    }

                    positiveButton(R.string.ok) {
                        if (configData.isNotEmpty()) {
                            recyclerAdapter.data += JsonObject(transformData(configData))
                            recyclerAdapter.notifyItemInserted(recyclerAdapter.data.size)
                            notifyUpdated(recyclerAdapter.data)
                        }
                    }
                    negativeButton(R.string.cancel)
                }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        itemTouchCallback?.setEnabled(enabled)
    }

    override fun onDestroyed() {
        itemTouchCallback = null
    }

    private fun createItemTouchAdapter(recyclerAdapter: ListRecyclerAdapter): ListRecyclerTouchCallback =
        ListRecyclerTouchCallback(recyclerAdapter, ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.RIGHT)

    private fun PrimitiveTypedString.toJsonElement(json: Json = Json): JsonElement {
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

    @Serializable
    private data class PrimitiveTypedString(
        val type : String,
        val value: String,
    ) {
        fun cast(): Any {
            return when(type) {
                "String" -> value
                "Boolean" -> value.toBoolean()
                "Float" -> value.toFloat()
                "Int" -> value.toInt()
                "Long" -> value.toLong()
                "Double" -> value.toDouble()
                //else -> Class.forName(type).cast(value)
                else -> error("Un-castable type: $type")
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> castAs(): T = cast() as T
    }

    class ListViewHolder(itemView: View): DefaultViewHolder(itemView) {

        val buttonAddList: Button = itemView.findViewById(R.id.buttonAdd)
        val recyclerViewList: RecyclerView = itemView.findViewById(R.id.recyclerView)

        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            buttonAddList.isEnabled    = enabled
            recyclerViewList.isEnabled = enabled
            (recyclerViewList.adapter as ListRecyclerAdapter?)?.setEnabled(enabled)
        }
    }
}

class ListRecyclerTouchCallback(
    private val recyclerAdapter: ListRecyclerAdapter,
    private val dragDirs: Int,
    private val swipeDirs: Int,
): SimpleCallback(dragDirs, swipeDirs) {

    private var isEnabled: Boolean = true

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPos: Int = viewHolder.bindingAdapterPosition
        val toPos: Int = target.bindingAdapterPosition
        recyclerAdapter.swapData(fromPos, toPos)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        recyclerAdapter.removeData(viewHolder.layoutPosition)
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        return if (isEnabled)
            makeMovementFlags(dragDirs, swipeDirs)
        else
            makeMovementFlags(0, 0)
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
}

class ListConfigBuilder: ConfigBuilderBase<ListConfigOption.ListViewHolder, List<JsonObject>>() {

    private var dataTransBlock  : DataTransformBlock       = { it }
    private var onDrawBlock     : OnDrawBlock              by notNull()
    private var onInflateBlock  : OnInflateBlock           by notNull()
    private var configStructure : List<ConfigOption<*, *>> by notNull()

    private var default: List<JsonObject> = emptyList()
    fun default(vararg entries: JsonObject) {
        default = entries.asList()
    }

    inline fun <reified T> default(vararg entries: T) {
        val transformed = entries.map { Json.encodeToJsonElement(it).jsonObject }
        default(*transformed.toTypedArray())
    }

    fun onInflate(block: OnInflateBlock) {
        onInflateBlock = block
    }

    fun onDraw(block: OnDrawBlock) {
        onDrawBlock = block
    }

    fun transformData(block: DataTransformBlock) {
        this.dataTransBlock = block
    }

    @JvmName("InlineOnDraw")
    inline fun <reified T> onDraw(crossinline block: (view: View, data: T) -> Unit) =
        onDraw { view, data ->
            block(view, Json.decodeFromJsonElement(data))
        }

    fun structure(block: ConfigItemBuilder.() -> Unit) {
        configStructure = ConfigItemBuilder().apply(block).build()
    }

    override fun build(): ListConfigOption =
        ListConfigOption(
            id = id,
            title = title!!,
            description = description,
            icon = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            dependency = dependency,
            onInflate = onInflateBlock,
            onDraw = onDrawBlock,
            transformData = dataTransBlock,
            structure = configStructure,
            default = default
        )
}

@ConfigOptionBuilderDsl
fun ConfigItemBuilder.list(block: ListConfigBuilder.() -> Unit) {
    add(ListConfigBuilder().apply(block))
}
