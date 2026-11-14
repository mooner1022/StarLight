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
import kotlinx.serialization.json.JsonElement
import java.util.*

private val LOG = LoggerFactory.logger {  }

const val EXTRA_TITLE = "title"
const val EXTRA_SUBTITLE = "subTitle"
const val EXTRA_ACTIVITY_ID = "activityId"

/**
 * What a settings screen is made of, which an intent can't carry: the structure and the callbacks of whoever opened
 * it. It is kept here, by the screen's id, from the time the screen is asked for until it is closed for good, so
 * that the screen finds it again each time Android makes it anew (a rotation, a switch to dark mode, a fold).
 */
data class DataHolder(
    val onCreatedListener : ConfigActivity.() -> Unit = {},
    val structBlock       : ConfigActivity.() -> ConfigStructure,
    val saved             : MutableDataMap,
    val publisher         : MutableSharedFlow<ApplicationEvent.ConfigActivity>,
    val flow              : Flow<ApplicationEvent.ConfigActivity>
)

private val holders: MutableMap<String, DataHolder> = hashMapOf()

/** The first settings screen of the ones open now: the ones opened from it go when it goes. */
private var rootActivityId: String? = null

fun Context.startConfigActivity(configActivity: dev.mooner.starlight.utils.ConfigActivity) {
    startConfigActivity(
        uuid = configActivity.uuid,
        title = configActivity.title,
        subTitle = configActivity.subTitle,
        structBlock = configActivity::getStructBlock,
        saved = configActivity.getSavedData(),
        onCreated = configActivity::onCreated,
        onValueUpdated = configActivity::onValueUpdated,
        onDestroyed = configActivity::onDestroyed,
    )
}

fun Context.startConfigActivity(
    uuid           : String? = null,
    title          : String,
    subTitle       : String,
    struct         : ConfigStructure,
    saved          : MutableDataMap = hashMapOf(),
    onCreated      : ConfigActivity.() -> Unit = {},
    onValueUpdated : OnValueUpdatedListener = { _, _, _, _ -> },
    onDestroyed    : () -> Unit = {}
) = startConfigActivity(uuid, title, subTitle, { struct }, saved, onCreated, onValueUpdated, onDestroyed)

fun Context.startConfigActivity(
    uuid           : String? = null,
    title          : String,
    subTitle       : String,
    structBlock    : ConfigActivity.() -> ConfigStructure,
    saved          : MutableDataMap = hashMapOf(),
    onCreated      : ConfigActivity.() -> Unit = {},
    onValueUpdated : OnValueUpdatedListener = { _, _, _, _ -> },
    onDestroyed    : () -> Unit = {}
) {
    if (this is Activity && this !is ConfigActivity) {
        holders.keys.toList().forEach(::finishConfigActivity)
        holders.clear()
        rootActivityId = null
    }

    val activityId = uuid ?: UUID.randomUUID().toString()

    val publisher: MutableSharedFlow<ApplicationEvent.ConfigActivity> =
        MutableSharedFlow(extraBufferCapacity = Channel.UNLIMITED)

    val flow = publisher
        .onEach { event ->
            LOG.verbose { event }
            when(event) {
                is ApplicationEvent.ConfigActivity.Create -> {}
                    //onCreated(event.activity)
                is ApplicationEvent.ConfigActivity.Update ->
                    with(event.data!!) {
                        onValueUpdated(parentId, id, data, jsonData) }
                is ApplicationEvent.ConfigActivity.Destroy ->
                    onDestroyed()
            }
        }

    holders[activityId] = DataHolder(onCreated, structBlock, saved, publisher, flow)
    val intent = Intent(this, ConfigActivity::class.java).apply {
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_SUBTITLE, subTitle)
        putExtra(EXTRA_ACTIVITY_ID, activityId)
    }
    startActivity(intent)

    LOG.verbose { "Starting new config activity with ID: $activityId, pending for activity callback" }
}

fun finishConfigActivity(id: String): Boolean {
    if (id !in holders)
        return false
    runBlocking {
        EventHandler.fireEvent(
            ApplicationEvent.ConfigActivity.Destroy(id)
        )
    }
    return true
}

/**
 * Whether the screen with this id can still be shown. It can't once the process that opened it is gone: Android
 * brings the screen back, but what it showed was never saved anywhere.
 */
internal fun hasConfigHolder(activityId: String): Boolean =
    activityId in holders

internal fun ConfigActivity.initAdapter() {
    activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)!!
    holder = holders[activityId] ?: error("Failed to find holder with id $activityId")
    // In this instance's scope, each time: the ones of an instance Android replaced ended with it, and a screen left
    // without them looks the same but no longer tells anyone of a change.
    holder.flow
        .onCompletion { LOG.verbose { "HandlerFlow completed" } }
        .launchIn(eventHandlerScope)

    EventHandler.eventFlow
        .buffer(Channel.UNLIMITED)
        .filterIsInstance<ApplicationEvent.ConfigActivity>()
        .filter { it.uuid == activityId }
        .onEach(holder.publisher::emit)
        .onCompletion { LOG.verbose { "EventFlow completed" } }
        .launchIn(eventHandlerScope)

    if (rootActivityId == null) {
        rootActivityId = activityId
        LOG.verbose { "Setting new root activity as $rootActivityId" }
    }

    holder.onCreatedListener.invoke(this)
}

/**
 * The screen was closed for good, not taken down to be made anew: what it was made of is let go. With the first
 * screen go the ones opened from it.
 */
internal fun ConfigActivity.onClosed() {
    if (rootActivityId == activityId) {
        holders.clear()
        rootActivityId = null
        LOG.verbose { "Cleared holder data map" }
    } else {
        holders -= activityId
    }
}

abstract class ConfigActivity(val uuid: String? = null) {
    /*
    fun show(context: Context) {
        context.startConfigActivity(
            uuid = uuid,
            title = title,
            subTitle = subTitle,
            structBlock = ::getStructBlock,
            saved = getSavedData(),
            onCreated = ::onCreated,
            onValueUpdated = ::onValueUpdated,
            onDestroyed = ::onDestroyed,
        )
    }
     */

    abstract val title: String

    abstract val subTitle: String

    abstract fun getStructBlock(activity: ConfigActivity): ConfigStructure

    abstract fun getSavedData(): MutableDataMap

    abstract fun onCreated(activity: ConfigActivity)

    abstract suspend fun onValueUpdated(parentId: String, id: String, value: Any, jsonValue: JsonElement)

    abstract fun onDestroyed()
}