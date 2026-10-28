package dev.mooner.starlight.ui.plugins.store

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.plugin.PluginChange
import dev.mooner.starlight.plugincore.plugin.PluginDependency
import dev.mooner.starlight.plugincore.plugin.PluginRepository
import dev.mooner.starlight.plugincore.version.Version
import dev.mooner.starlight.ui.plugins.knownPlugins
import dev.mooner.starlight.ui.plugins.pluginInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

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

data class StoreInstallError(
    val pluginName: String,
    val error: Throwable,
)

enum class StoreSort {
    UPDATED, NAME
}

class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private sealed interface Task {
        data class Downloading(val progress: Int): Task
        data object Verifying: Task
        data object Done: Task
    }

    private val _state = MutableStateFlow<StoreLoadState>(StoreLoadState.Loading)
    val state: StateFlow<StoreLoadState> = _state

    private val installed = MutableStateFlow(installedVersions())
    private val tasks = MutableStateFlow(pendingTasks())
    private val installLock = Mutex()

    private val _errors = Channel<StoreInstallError>(Channel.BUFFERED)
    val errors: Flow<StoreInstallError> = _errors.receiveAsFlow()

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

    fun load() {
        loadJob?.cancel()
        hasRevealedContent = false
        installed.value = installedVersions()
        _state.value = StoreLoadState.Loading
        loadJob = viewModelScope.launch {
            _state.value = try {
                StoreLoadState.Loaded(StoreSource.fetchIndex())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StoreLoadState.Failed(e)
            }
        }
    }

    fun installStateOf(id: String): StoreInstallState? =
        installStates.value[id]

    fun installedVersionOf(id: String): Version? =
        installed.value[id]

    fun missingDependencies(release: StoreRelease): List<PluginDependency> =
        release.dependency.filter { dependency ->
            val version = installed.value[dependency.pluginId]
            !dependency.isOptional && (version == null || !dependency.accepts(version))
        }

    fun install(plugin: StorePlugin, release: StoreRelease = plugin.latest, dependencies: List<String> = emptyList()) {
        val index = (state.value as? StoreLoadState.Loaded)?.index ?: return
        val queue = dependencies.mapNotNull { id -> index.find(id)?.let { it to it.latest } } + (plugin to release)
        val pending = queue.filter { (target, _) -> tasks.value[target.id].let { it == null || it == Task.Done } }
        for ((target, _) in pending)
            setTask(target.id, Task.Downloading(0))
        val repository = PluginRepository(index.name, StoreSource.REPOSITORY_URL)
        viewModelScope.launch {
            installLock.withLock {
                for ((target, targetRelease) in pending) {
                    try {
                        installRelease(target, targetRelease, repository)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        for ((rest, _) in pending.dropWhile { it.first != target })
                            clearTask(rest.id)
                        _errors.send(StoreInstallError(target.name, e))
                        break
                    }
                }
            }
        }
    }

    private suspend fun installRelease(plugin: StorePlugin, release: StoreRelease, repository: PluginRepository) {
        val context = getApplication<Application>()
        val file = context.cacheDir.resolve("store").apply { mkdirs() }.resolve(release.fileName)
        try {
            setTask(plugin.id, Task.Downloading(0))
            StoreSource.download(StoreSource.resolve(release.url), file, release.size) { progress ->
                setTask(plugin.id, Task.Downloading(progress))
            }
            setTask(plugin.id, Task.Verifying)
            withContext(Dispatchers.IO) {
                val installer = context.pluginInstaller()
                val info = installer.inspect(file)
                if (info.id != plugin.id || info.version != release.version)
                    throw IOException("${release.fileName} contains ${info.id} ${info.version}")
                pluginManager.install(installer, file, release.sha256, repository)
            }
            installed.update { it + (plugin.id to release.version) }
            setTask(plugin.id, Task.Done)
        } finally {
            file.delete()
        }
    }

    private fun setTask(id: String, task: Task) =
        tasks.update { it + (id to task) }

    private fun clearTask(id: String) =
        tasks.update { it - id }

    private fun installedVersions(): Map<String, Version> =
        runCatching { knownPlugins().associate { it.id to it.version } }.getOrDefault(emptyMap())

    private fun pendingTasks(): Map<String, Task> =
        runCatching {
            pluginManager.changes.values
                .filter { it is PluginChange.Installed || it is PluginChange.Updated }
                .mapNotNull { it.info?.id }
                .associateWith<String, Task> { Task.Done }
        }.getOrDefault(emptyMap())

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
