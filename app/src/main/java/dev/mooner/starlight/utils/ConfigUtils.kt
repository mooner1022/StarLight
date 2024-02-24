package dev.mooner.starlight.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.configdsl.adapters.OnValueUpdatedListener
import dev.mooner.starlight.event.ApplicationEvent
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.ui.config.ConfigActivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

private val LOG = LoggerFactory.logger {  }

const val EXTRA_TITLE = "title"
const val EXTRA_SUBTITLE = "subTitle"
const val EXTRA_ACTIVITY_ID = "activityId"

data class DataHolder(
    var isPaused  : Boolean = false,
    val struct    : ConfigStructure,
    val saved     : MutableDataMap,
    val publisher : MutableSharedFlow<ApplicationEvent.ConfigActivity>,
    val flow      : Flow<ApplicationEvent.ConfigActivity>
)

//private var instanceCount: Int = 0
private val instanceCount: AtomicInteger = AtomicInteger(0)
private val holders: MutableMap<String, DataHolder> = hashMapOf()
//private var lastDestroyed: String? = null

private var rootActivityId: String? = null

fun Context.startConfigActivity(
    uuid           : String? = null,
    title          : String,
    subTitle       : String,
    struct         : ConfigStructure,
    saved          : MutableDataMap = hashMapOf(),
    onValueUpdated : OnValueUpdatedListener = { _, _, _, _ -> },
    onDestroy      : () -> Unit = {}
) {
    if (this is Activity && this !is ConfigActivity) {
        holders.keys.forEach(::finishConfigActivity)
        holders.clear()
        instanceCount.set(0)
    }

    val activityId = uuid ?: UUID.randomUUID().toString()

    val publisher: MutableSharedFlow<ApplicationEvent.ConfigActivity> =
        MutableSharedFlow(extraBufferCapacity = Channel.UNLIMITED)

    val flow = publisher
        .onEach { event ->
            LOG.verbose { event }
            when(event) {
                is ApplicationEvent.ConfigActivity.Update ->
                    with(event.data!!) {
                        onValueUpdated(parentId, id, data, jsonData) }
                is ApplicationEvent.ConfigActivity.Destroy ->
                    onDestroy()
            }
        }

    holders[activityId] = DataHolder(false, struct, saved, publisher, flow)
    val intent = Intent(this, ConfigActivity::class.java).apply {
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_SUBTITLE, subTitle)
        putExtra(EXTRA_ACTIVITY_ID, activityId)
    }
    startActivity(intent)

    LOG.verbose { "Starting new config activity with ID: $activityId, pending for activity callback" }
}

fun finishConfigActivity(id: String): Boolean {
    if (id in holders) {
        runBlocking {
            EventHandler.fireEvent(
                ApplicationEvent.ConfigActivity.Destroy(
                    uuid = id
                )
            )
        }
        return true
    }
    return false
}

internal fun ConfigActivity.initAdapter() {
    activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)!!
    holder = holders[activityId] ?: error("Failed to find holder with id $activityId")
    // Bypass instance count increment if activity was paused
    if (holder.isPaused) {
        instanceCount.getAndDecrement()
        holder.isPaused = false
    } else {
        holder.flow
            .onCompletion { LOG.verbose { "HandlerFlow completed" } }
            //.flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .launchIn(eventHandleScope)

        EventHandler.eventFlow
            //.flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .buffer(Channel.UNLIMITED)
            .filterIsInstance<ApplicationEvent.ConfigActivity.Update>()
            .filter { it.uuid == activityId }
            .onEach(holder.publisher::emit)
            .onCompletion { LOG.verbose { "EventFlow completed" } }
            .launchIn(eventHandleScope)

        instanceCount.getAndIncrement()
    }

    LOG.verbose { "Initialized, instanceCount= ${instanceCount.get()}" }

    if (rootActivityId == null) {
        rootActivityId = activityId
        LOG.verbose { "Setting new root activity as $rootActivityId" }
    }
}

internal fun ConfigActivity.onPaused() {
    instanceCount.getAndIncrement()
    holders[activityId]?.isPaused = true
    LOG.verbose { "Paused, instanceCount= ${instanceCount.get()}" }
}

context(ConfigActivity)
internal fun onDestroyed() {
    instanceCount.getAndDecrement()
    LOG.verbose { "Destroyed, instanceCount= ${instanceCount.get()}" }
    if (holders[activityId]?.isPaused == true) {
        instanceCount.getAndDecrement()
        LOG.verbose { "Decrementing paused instance, instanceCount= ${instanceCount.get()}" }
    }
    if (instanceCount.get() <= 0 || rootActivityId == activityId) {
        holders.clear()
        instanceCount.set(0)
        rootActivityId = null
        LOG.verbose { "Cleared holder data map" }

        if (instanceCount.get() < 0)
            instanceCount.set(0)
    }
}