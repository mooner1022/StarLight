package com.mooner.starlight.utils

import android.content.Context
import android.content.Intent
import android.view.View
import com.mooner.starlight.plugincore.config.CategoryConfigObject
import com.mooner.starlight.plugincore.config.TypedString
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.ui.config.ConfigActivity
import com.mooner.starlight.ui.config.ParentAdapter
import java.util.*

private const val T = "ConfigUtils"
const val EXTRA_TITLE = "title"
const val EXTRA_SUBTITLE = "subTitle"
const val EXTRA_ACTIVITY_ID = "activityId"

private data class DataHolder(
    val items: List<CategoryConfigObject>,
    val saved: Map<String, Map<String, TypedString>>,
    val listener: (parentId: String, id: String, view: View?, data: Any) -> Unit,
    val onDestroyed: () -> Unit,
    var instance: ConfigActivity? = null
)

private val holders: MutableMap<String, DataHolder> = hashMapOf()

fun Context.startConfigActivity(
    id: String? = null,
    title: String,
    subTitle: String,
    items: List<CategoryConfigObject>,
    saved: Map<String, Map<String, TypedString>> = mapOf(),
    onConfigChanged: (parentId: String, id: String, view: View?, data: Any) -> Unit = { _, _, _, _ -> },
    onDestroy: () -> Unit = {}
) {
    val activityId = id ?: UUID.randomUUID().toString()
    holders[activityId] = DataHolder(items, saved, onConfigChanged, onDestroy)
    val intent = Intent(this, ConfigActivity::class.java).apply {
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_SUBTITLE, subTitle)
        putExtra(EXTRA_ACTIVITY_ID, activityId)
    }
    startActivity(intent)
    Logger.v(T, "Starting new config activity with ID: $activityId")
}

fun finishConfigActivity(id: String): Boolean {
    if (id in holders) {
        val holder = holders[id]!!
        if (holder.instance != null) {
            Logger.v(T, "Finishing config activity with ID: $id")
            holder.instance!!.apply {
                onDestroyed()
                finish()
            }
            return true
        }
    }
    return false
}

internal fun ConfigActivity.initAdapter() {
    val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)!!
    val holder = holders[activityId]?: error("Failed to find holder with id $activityId")
    recyclerAdapter = ParentAdapter(
        context = binding.root.context,
        onConfigChanged = holder.listener
    ).apply {
        data = holder.items
        saved = holder.saved
        notifyItemRangeInserted(0, data.size)
    }
    holder.instance = this
}

internal fun ConfigActivity.onDestroyed() {
    val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)!!
    if (activityId in holders) {
        holders[activityId]!!.onDestroyed()
        holders -= activityId
        Logger.v(T, "onDestroyed() called from config activity with ID: $activityId")
    }
}