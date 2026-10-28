package dev.mooner.starlight.ui.plugins

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.ColorRes
import androidx.lifecycle.LifecycleOwner
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.checkbox.checkBoxPrompt
import com.afollestad.materialdialogs.checkbox.isCheckPromptChecked
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.plugin.InstalledPlugin
import dev.mooner.starlight.plugincore.plugin.PluginChange
import dev.mooner.starlight.plugincore.plugin.PluginExclusion
import dev.mooner.starlight.plugincore.plugin.PluginInfo
import dev.mooner.starlight.plugincore.plugin.PluginInstaller
import dev.mooner.starlight.plugincore.plugin.PluginState
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.utils.setCommonAttrsWithLifecycle
import java.io.File

internal data class PluginStatus(
    val text: String,
    @ColorRes val color: Int,
    val isProblem: Boolean = false,
)

internal fun Context.pluginInstaller(): PluginInstaller =
    PluginInstaller(getStarLightDirectory().resolve("plugins"), filesDir)

internal fun Context.pluginIconFile(id: String): File =
    filesDir.resolve("plugin_data").resolve(id).resolve("assets").resolve("icon.png")

internal fun PluginInfo.displayVersion(): String =
    "v$version"

internal fun Context.pluginStatus(entry: InstalledPlugin?, change: PluginChange?): PluginStatus {
    when (change) {
        is PluginChange.Removed ->
            return PluginStatus(getString(R.string.plugin_status_removed), R.color.text_sub)
        is PluginChange.Installed ->
            return PluginStatus(getString(R.string.plugin_status_installed), R.color.main_bright)
        is PluginChange.Updated ->
            return PluginStatus(
                getString(R.string.plugin_status_updated, "v${change.from}", change.info.displayVersion()),
                R.color.main_bright
            )
        null -> {}
    }
    entry ?: return PluginStatus("", R.color.text_sub)
    val info = entry.info
    val version = info?.displayVersion().orEmpty()
    val state = entry.state
    if (info != null && !entry.isDuplicate && state !is PluginState.Disabled && !pluginManager.isPluginEnabled(info.id))
        return PluginStatus(getString(R.string.plugin_status_disable_pending), R.color.main_bright)
    return when (state) {
        is PluginState.Loaded ->
            PluginStatus(
                listOf(version, info!!.authors.joinToString(), Formatter.formatShortFileSize(this, entry.file.length()))
                    .filter(String::isNotBlank)
                    .joinToString(" · "),
                R.color.card_enabled
            )
        PluginState.Disabled ->
            if (pluginManager.isPluginEnabled(info!!.id))
                PluginStatus(getString(R.string.plugin_status_enable_pending), R.color.main_bright)
            else
                PluginStatus("${getString(R.string.plugin_status_disabled)} · $version", R.color.text_sub)
        PluginState.SafeMode ->
            PluginStatus("${getString(R.string.plugin_status_safe_mode)} · $version", R.color.text_sub)
        is PluginState.Excluded ->
            PluginStatus(exclusionText(state.exclusion), R.color.orange, isProblem = true)
        is PluginState.Failed ->
            PluginStatus(
                getString(R.string.plugin_status_failed, state.error.message ?: state.error.javaClass.simpleName),
                R.color.code_error,
                isProblem = true
            )
        is PluginState.Invalid ->
            PluginStatus(getString(R.string.plugin_status_invalid), R.color.code_error, isProblem = true)
    }
}

internal fun Context.exclusionText(exclusion: PluginExclusion): String =
    when (exclusion) {
        PluginExclusion.Disabled ->
            getString(R.string.plugin_status_disabled)
        is PluginExclusion.IncompatibleApi ->
            getString(R.string.plugin_status_incompatible_api, exclusion.apiVersion.toString(), Info.PLUGINCORE_VERSION.toString())
        is PluginExclusion.MissingDependency ->
            getString(R.string.plugin_status_missing_dependency, exclusion.dependency.pluginId)
        is PluginExclusion.ExcludedDependency ->
            getString(R.string.plugin_status_excluded_dependency, exclusion.dependency.pluginId)
        is PluginExclusion.IncompatibleDependency ->
            getString(
                R.string.plugin_status_incompatible_dependency,
                exclusion.dependency.pluginId,
                exclusion.dependency.supportedVersion,
                exclusion.found.toString()
            )
        is PluginExclusion.DuplicateId ->
            getString(R.string.plugin_status_duplicate, exclusion.kept.name)
        PluginExclusion.CircularDependency ->
            getString(R.string.plugin_status_circular)
    }

internal fun Context.pluginsSubtitle(): String {
    val installed = pluginManager.installed
    val issues = installed.count { it.key !in pluginManager.changes && pluginStatus(it, null).isProblem }
    val count = installed.size + pluginManager.changes.count { (key, change) ->
        change is PluginChange.Installed && installed.none { it.key == key }
    }
    return if (issues == 0)
        getString(R.string.subtitle_plugins).format(count)
    else
        getString(R.string.subtitle_plugins_issues, count, issues)
}

internal fun requiredDependentsOf(id: String): List<String> =
    knownPlugins()
        .filter { info ->
            info.id != id &&
                    pluginManager.isPluginEnabled(info.id) &&
                    info.dependency.any { !it.isOptional && it.pluginId == id }
        }
        .map(PluginInfo::name)

internal fun knownPlugins(): List<PluginInfo> {
    val changes = pluginManager.changes
    val installed = pluginManager.installed
        .filter { !it.isDuplicate && changes[it.key] !is PluginChange.Removed }
        .mapNotNull { changes[it.key]?.info ?: it.info }
    val pending = changes.values
        .filterIsInstance<PluginChange.Installed>()
        .map { it.info }
    return (installed + pending).distinctBy(PluginInfo::id)
}

internal fun LifecycleOwner.showRemovePluginDialog(
    context: Context,
    key: String,
    onRemoved: (name: String) -> Unit,
    onFailure: (e: Throwable) -> Unit,
) {
    val entry = pluginManager.getInstalled(key)
    val info = pluginManager.changes[key]?.info ?: entry?.info
    val name = info?.name ?: entry?.file?.name ?: key
    val dependents = info?.takeUnless { entry?.isDuplicate == true }
        ?.let { requiredDependentsOf(it.id) }
        .orEmpty()
    val message = buildString {
        append(context.getString(R.string.plugin_remove_message))
        if (dependents.isNotEmpty())
            append("\n\n").append(context.getString(R.string.plugin_dependents_warning, dependents.joinToString()))
    }

    MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
        setCommonAttrsWithLifecycle()
        title(text = context.getString(R.string.plugin_remove_title, name))
        message(text = message)
        if (info != null && entry?.isDuplicate != true)
            checkBoxPrompt(res = R.string.plugin_remove_delete_data) {}
        positiveButton(res = R.string.plugin_remove) {
            val deleteData = info != null && entry?.isDuplicate != true && isCheckPromptChecked()
            runCatching { pluginManager.uninstall(context.pluginInstaller(), key, keepData = !deleteData) }
                .onSuccess { onRemoved(name) }
                .onFailure(onFailure)
        }
        negativeButton(res = R.string.cancel)
    }
}
