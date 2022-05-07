package dev.mooner.starlight.utils

import android.content.Context
import android.content.Intent
import android.view.View
import dev.mooner.starlight.plugincore.config.ConfigStructure
import dev.mooner.starlight.plugincore.config.TypedString
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.ui.config.ConfigActivity
import dev.mooner.starlight.ui.config.OnConfigChangedListener
import dev.mooner.starlight.ui.config.ParentRecyclerAdapter
import java.util.*

private const val TAG = "ConfigUtils"
const val EXTRA_TITLE = "title"
const val EXTRA_SUBTITLE = "subTitle"
const val EXTRA_ACTIVITY_ID = "activityId"

private data class DataHolder(
    val struct: ConfigStructure,
    val saved: Map<String, Map<String, TypedString>>,
    val listener: (parentId: String, id: String, view: View?, data: Any) -> Unit,
    val onDestroyed: () -> Unit,
    var instance: ConfigActivity? = null
)

private val holders: MutableMap<String, DataHolder> = hashMapOf()
private var lastDestroyed: String? = null

fun Context.startConfigActivity(
    id: String? = null,
    title: String,
    subTitle: String,
    struct: ConfigStructure,
    saved: Map<String, Map<String, TypedString>> = emptyMap(),
    onConfigChanged: OnConfigChangedListener = { _, _, _, _ -> },
    onDestroy: () -> Unit = {}
) {
    val activityId = id ?: UUID.randomUUID().toString()
    holders[activityId] = DataHolder(struct, saved, onConfigChanged, onDestroy)
    val intent = Intent(this, ConfigActivity::class.java).apply {
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_SUBTITLE, subTitle)
        putExtra(EXTRA_ACTIVITY_ID, activityId)
    }
    startActivity(intent)
    Logger.v(TAG, "Starting new config activity with ID: $activityId")
}

fun finishConfigActivity(id: String): Boolean {
    if (id in holders) {
        val holder = holders[id]!!
        if (holder.instance != null) {
            Logger.v(TAG, "Finishing config activity with ID: $id")
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
    activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)!!
    val holder = holders[activityId] ?: error("Failed to find holder with id $activityId")
    recyclerAdapter = ParentRecyclerAdapter(
        context = binding.root.context,
        configStructure = holder.struct,
        savedData = holder.saved,
        onConfigChanged = holder.listener
    ).apply(ParentRecyclerAdapter::notifyAllItemInserted)
    holder.instance = this

    if (lastDestroyed != null && lastDestroyed != activityId) {
        holders -= lastDestroyed!!
    }
}

internal fun ConfigActivity.onDestroyed() {
    val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)!!
    if (activityId in holders) {
        holders[activityId]!!.let {
            it.onDestroyed()
            it.instance = null
        }
        lastDestroyed = activityId
        //holders -= activityId
        Logger.v(TAG, "onDestroyed() called from config activity with ID: $activityId")
    }
}