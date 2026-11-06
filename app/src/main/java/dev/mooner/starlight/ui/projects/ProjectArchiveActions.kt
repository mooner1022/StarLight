package dev.mooner.starlight.ui.projects

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.utils.peek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Asks where to save [project] as a zip and writes it there. */
internal fun ComponentActivity.exportProject(project: Project) {
    val name = project.info.name
    var launcher: ActivityResultLauncher<String>? = null
    // Registered on demand from a settings row, so it is removed once the picker returns.
    launcher = activityResultRegistry.register(
        "export_project_${project.info.id}",
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        launcher?.unregister()
        if (uri == null)
            return@register
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)!!.use { ProjectArchive.export(project, it) }
                }
            }
                .onSuccess { peek(getString(R.string.project_export_done, name), PeekType.SUCCESS) }
                .onFailure { e -> peek(getString(R.string.project_export_failed, e.message ?: e.toString()), PeekType.ERROR) }
        }
    }
    launcher.launch("$name.zip")
}

/** Imports the project zip at [uri] picked by the user. */
internal fun LifecycleOwner.importProject(context: Context, uri: Uri) {
    lifecycleScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)!!.use(ProjectArchive::import)
            }
        }
            .onSuccess { project -> context.peek(context.getString(R.string.project_import_done, project.info.name), PeekType.SUCCESS) }
            .onFailure { e -> context.peek(context.getString(R.string.project_import_failed, e.message ?: e.toString()), PeekType.ERROR) }
    }
}
