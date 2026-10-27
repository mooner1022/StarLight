package dev.mooner.starlight.stats

import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object ScriptWidgetEntries {

    data class Entry(
        val projectId: ProjectID,
        val projectName: String,
        val key: String,
        val label: String,
        val value: String,
        val updatedMillis: Long,
    )

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isStarted = AtomicBoolean(false)

    private val entries: ConcurrentHashMap<Pair<ProjectID, String>, Entry> = ConcurrentHashMap()

    private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val updates: SharedFlow<Unit>
        get() = _updates

    fun start() {
        if (isStarted.compareAndSet(false, true))
            EventHandler.on(scope, ::onProjectDelete)
    }

    fun all(): List<Entry> =
        entries.values.sortedByDescending(Entry::updatedMillis)

    fun set(project: Project, key: String, label: String, value: String) {
        val id = project.info.id
        entries[id to key] = Entry(id, project.info.name, key, label, value, System.currentTimeMillis())
        _updates.tryEmit(Unit)
    }

    fun remove(project: Project, key: String) {
        if (entries.remove(project.info.id to key) != null)
            _updates.tryEmit(Unit)
    }

    fun clear(project: Project) =
        clear(project.info.id)

    private fun clear(projectId: ProjectID) {
        if (entries.keys.removeAll { (id, _) -> id == projectId })
            _updates.tryEmit(Unit)
    }

    private fun onProjectDelete(event: Events.Project.Delete) =
        clear(event.projectId)
}
