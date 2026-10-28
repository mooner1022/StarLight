package dev.mooner.starlight.ui.plugins.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.plugin.PluginDependency
import dev.mooner.starlight.plugincore.version.Version
import dev.mooner.starlight.ui.plugins.knownPlugins
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface StoreLoadState {
    data object Loading: StoreLoadState
    data class Loaded(val index: StoreIndex): StoreLoadState
    data class Failed(val error: Throwable): StoreLoadState
}

sealed interface StoreInstallState {
    data object NotInstalled: StoreInstallState
    data class UpdateAvailable(val installed: Version): StoreInstallState
    data object Installed: StoreInstallState
    data class Downloading(val progress: Int): StoreInstallState
    data object Verifying: StoreInstallState
    data object PendingRestart: StoreInstallState
    data object Incompatible: StoreInstallState

    val isBusy: Boolean
        get() = this is Downloading || this == Verifying
}

enum class StoreSort {
    UPDATED, NAME, DOWNLOADS
}

class StoreViewModel : ViewModel() {

    private sealed interface Task {
        data class Downloading(val progress: Int): Task
        data object Verifying: Task
        data object Done: Task
    }

    private val _state = MutableStateFlow<StoreLoadState>(StoreLoadState.Loading)
    val state: StateFlow<StoreLoadState> = _state

    private val installed = MutableStateFlow(
        SampleStoreSource.sampleInstalled +
                runCatching { knownPlugins().associate { it.id to it.version } }.getOrDefault(emptyMap())
    )
    private val tasks = MutableStateFlow<Map<String, Task>>(emptyMap())

    val installStates: StateFlow<Map<String, StoreInstallState>> =
        combine(_state, installed, tasks) { state, installed, tasks ->
            val index = (state as? StoreLoadState.Loaded)?.index ?: return@combine emptyMap()
            index.plugins.associate { plugin -> plugin.id to stateOf(plugin, installed[plugin.id], tasks[plugin.id]) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val pendingRestart: StateFlow<Int> =
        tasks.map { tasks -> tasks.values.count { it == Task.Done } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    var query: String = ""
    var category: String? = null
    var sort: StoreSort = StoreSort.UPDATED
    var hasRevealedContent: Boolean = false

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load(simulateFailure: Boolean = false) {
        loadJob?.cancel()
        hasRevealedContent = false
        _state.value = StoreLoadState.Loading
        loadJob = viewModelScope.launch {
            _state.value = runCatching { SampleStoreSource.fetchIndex(simulateFailure) }
                .fold(StoreLoadState::Loaded, StoreLoadState::Failed)
        }
    }

    fun installStateOf(id: String): StoreInstallState? =
        installStates.value[id]

    fun installedVersionOf(id: String): Version? =
        installed.value[id]

    fun missingDependencies(plugin: StorePlugin): List<PluginDependency> =
        plugin.latest.dependency
            .filter { !it.isOptional && it.pluginId !in installed.value && tasks.value[it.pluginId] == null }

    fun install(ids: Collection<String>) {
        val index = (state.value as? StoreLoadState.Loaded)?.index ?: return
        for (id in ids) {
            val plugin = index.find(id) ?: continue
            if (tasks.value[id].let { it != null && it != Task.Done })
                continue
            viewModelScope.launch { simulateInstall(plugin) }
        }
    }

    private suspend fun simulateInstall(plugin: StorePlugin) {
        val id = plugin.id
        var progress = 0
        setTask(id, Task.Downloading(0))
        delay(250)
        while (progress < 100) {
            progress = (progress + Random.nextInt(4, 13)).coerceAtMost(100)
            setTask(id, Task.Downloading(progress))
            delay(Random.nextLong(40, 110))
        }
        setTask(id, Task.Verifying)
        delay(700)
        installed.update { it + (id to plugin.latest.version) }
        setTask(id, Task.Done)
    }

    private fun setTask(id: String, task: Task) =
        tasks.update { it + (id to task) }

    private fun stateOf(plugin: StorePlugin, installed: Version?, task: Task?): StoreInstallState =
        when {
            task is Task.Downloading -> StoreInstallState.Downloading(task.progress)
            task == Task.Verifying   -> StoreInstallState.Verifying
            task == Task.Done        -> StoreInstallState.PendingRestart
            plugin.latest.apiVersion incompatibleWith Info.PLUGINCORE_VERSION -> StoreInstallState.Incompatible
            installed == null        -> StoreInstallState.NotInstalled
            installed < plugin.latest.version -> StoreInstallState.UpdateAvailable(installed)
            else                     -> StoreInstallState.Installed
        }
}
