package dev.mooner.starlight.ui.plugins.setup

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import dev.mooner.configdsl.DataMap
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.configdsl.adapters.ConfigAdapter
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.data.InMemoryConfig
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.plugin.StarlightPlugin
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.ui.common.showSheet
import dev.mooner.starlight.ui.plugins.pluginIconFile
import dev.mooner.starlight.utils.peek
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentHashMap

private val LOG = LoggerFactory.logger {  }

/** Whether the plugin asked for its setup. A plugin that fails to answer is treated as not asking. */
internal fun StarlightPlugin.needsSetup(): Boolean =
    pluginCall { isSetupRequired() } ?: false

/** Whether the plugin has a setup dialog to show. */
internal fun StarlightPlugin.hasSetup(): Boolean =
    pluginCall { getSetupStructure().isNotEmpty() } ?: false

private fun StarlightPlugin.setupReason(): String? =
    pluginCall { getSetupReason() }?.takeIf(String::isNotBlank)

/** Loaded plugins that asked for their setup, by name. */
internal fun pluginsNeedingSetup(): List<StarlightPlugin> =
    pluginManager.plugins
        .filter(StarlightPlugin::needsSetup)
        .sortedBy { it.info.name }

/**
 * Opens the plugin's setup dialog and hands what is entered to [StarlightPlugin.onSetup], without
 * saving it. Returns `false`, showing nothing, when the plugin has nothing to set up.
 *
 * @param onClosed called once the dialog closes, with whether the plugin accepted the values.
 */
internal fun Context.showPluginSetupSheet(
    plugin: StarlightPlugin,
    owner: LifecycleOwner,
    onClosed: (isDone: Boolean) -> Unit = {},
): Boolean {
    val id = plugin.info.id
    // A launch prompt and a request from the plugin can arrive together.
    if (id in openSetups)
        return false
    val structure = plugin.pluginCall { getSetupStructure() }
        ?.takeIf { it.isNotEmpty() }
        ?: return false
    val reason = plugin.setupReason()

    // ConfigAdapter only stores values into categories already in the map, and does so on an IO thread.
    val values: MutableDataMap = ConcurrentHashMap()
    for (option in structure)
        values[option.id] = ConcurrentHashMap()

    // Inside the sheet's scroll view, which lays out every item: setup forms are short.
    val recyclerView = RecyclerView(this).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        isNestedScrollingEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    // Destroyed when the sheet closes rather than with the owner, which outlives the sheet.
    val adapter = ConfigAdapter.Builder(this) {
        bind(recyclerView)
        structure { structure }
        configData(values)
        onValueUpdated { parentId, key, _, jsonValue ->
            values.getOrPut(parentId) { ConcurrentHashMap() }[key] = jsonValue
        }
    }.build()

    val doneText = getString(R.string.plugin_setup_done)
    val iconFile = pluginIconFile(plugin.info.id).takeIf { it.isFile }
    var isDone = false
    var job: Job? = null

    val shown = showSheet {
        lifecycleOwner = owner
        title = getString(R.string.plugin_setup_title, plugin.info.name)
        message = reason ?: getString(R.string.plugin_setup_message)
        if (iconFile != null)
            iconLoader = { load(iconFile) { scale(Scale.FIT) } }
        else
            icon = R.drawable.ic_round_construction_24
        content(recyclerView)
        positive(doneText, autoDismiss = false) { sheet ->
            if (job?.isActive == true)
                return@positive
            if (adapter.hasError) {
                sheet.setNote(getString(R.string.plugin_setup_invalid), isError = true)
                return@positive
            }
            sheet.setNote(null)
            sheet.setPositiveEnabled(false)
            sheet.setPositiveText(getString(R.string.plugin_setup_checking))
            job = owner.lifecycleScope.launch {
                val snapshot: DataMap = values.mapValues { (_, entries) -> entries.toMap() }
                val error = try {
                    plugin.onSetup(InMemoryConfig(snapshot))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    plugin.logError(e)
                    getString(R.string.plugin_setup_error, e.message ?: e.toString())
                } catch (e: LinkageError) {
                    plugin.logError(e)
                    getString(R.string.plugin_setup_error, e.message ?: e.toString())
                }
                if (error == null) {
                    isDone = true
                    sheet.dismiss()
                    peek(getString(R.string.plugin_setup_completed, plugin.info.name), PeekType.SUCCESS, PeekAlert.Position.Top)
                } else {
                    sheet.setNote(error, isError = true)
                    sheet.setPositiveEnabled(true)
                    sheet.setPositiveText(doneText)
                }
            }
        }
        negative(R.string.plugin_setup_later)
        onDismiss {
            openSetups -= id
            job?.cancel()
            adapter.destroy()
            if (isDone)
                PluginSetupPrompter.forgetPutOff(id)
            // Closed by the user, not by its screen going away (e.g. on rotation).
            else if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED))
                PluginSetupPrompter.putOff(id, reason)
            onClosed(isDone)
        }
    }
    openSetups += id
    shown.dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    return true
}

/** Plugins whose setup dialog is open. Touched on the main thread only. */
private val openSetups: MutableSet<String> = hashSetOf()

internal object PluginSetupPrompter {

    private const val CATEGORY = "plugin"
    // Plugin id -> the setup reason the user put off ("" for none).
    private const val KEY_PUT_OFF = "setup_put_off"

    private var hasPromptedOnLaunch = false

    /**
     * Asks, once per process, for the setup of every loaded plugin that needs it, except those the
     * user already put off for the same reason.
     */
    fun promptOnLaunch(context: Context, owner: LifecycleOwner) {
        if (hasPromptedOnLaunch)
            return
        hasPromptedOnLaunch = true
        promptAll(context, owner, pluginsNeedingSetup(), skipPutOff = true)
    }

    /** Answers [StarlightPlugin.requestSetup], unless the user already put it off for the same reason. */
    fun promptRequested(context: Context, owner: LifecycleOwner, plugin: StarlightPlugin) =
        promptAll(context, owner, listOf(plugin), skipPutOff = true)

    /** Opens the setup of each plugin that still needs it, one after another, then calls [onFinished]. */
    fun promptAll(
        context    : Context,
        owner      : LifecycleOwner,
        plugins    : List<StarlightPlugin>,
        skipPutOff : Boolean = false,
        onFinished : () -> Unit = {},
    ) {
        // The sheet also closes when its owner is destroyed; don't open the next one on a dead screen.
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED))
            return
        val plugin = plugins.firstOrNull()
            ?: return onFinished()
        val rest = plugins.drop(1)
        val shown = plugin.needsSetup() &&
                !(skipPutOff && isPutOff(plugin)) &&
                context.showPluginSetupSheet(plugin, owner) {
                    promptAll(context, owner, rest, skipPutOff, onFinished)
                }
        if (!shown)
            promptAll(context, owner, rest, skipPutOff, onFinished)
    }

    private fun isPutOff(plugin: StarlightPlugin): Boolean =
        putOffReasons()[plugin.info.id] == plugin.setupReason().orEmpty()

    /** Remembers that the user closed the setup without finishing it, so launches don't ask again. */
    fun putOff(id: String, reason: String?) =
        savePutOff(putOffReasons() + (id to reason.orEmpty()))

    fun forgetPutOff(id: String) {
        val reasons = putOffReasons()
        if (id in reasons)
            savePutOff(reasons - id)
    }

    private fun putOffReasons(): Map<String, String> =
        GlobalConfig.category(CATEGORY)
            .getObject(KEY_PUT_OFF)
            ?.mapValues { (_, value) -> (value as? JsonPrimitive)?.contentOrNull.orEmpty() }
            .orEmpty()

    private fun savePutOff(reasons: Map<String, String>) =
        GlobalConfig.edit {
            category(CATEGORY).setRaw(KEY_PUT_OFF, JsonObject(reasons.mapValues { (_, reason) -> JsonPrimitive(reason) }))
        }
}

private inline fun <T> StarlightPlugin.pluginCall(block: StarlightPlugin.() -> T): T? =
    try {
        block()
    } catch (e: Exception) {
        logError(e)
        null
    } catch (e: LinkageError) {
        logError(e)
        null
    }

private fun StarlightPlugin.logError(error: Throwable) =
    LOG.error {
        translate {
            Locale.ENGLISH { "Plugin ${info.name} failed while handling its setup: $error" }
            Locale.KOREAN  { "플러그인 ${info.name}의 초기 설정을 처리하다 오류가 났어요: $error" }
        }
    }
