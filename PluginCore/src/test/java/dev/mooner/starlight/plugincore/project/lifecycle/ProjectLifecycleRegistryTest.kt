package dev.mooner.starlight.plugincore.project.lifecycle

import dev.mooner.starlight.plugincore.config.data.MutableConfig
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.logger.ProjectLogger
import dev.mooner.starlight.plugincore.pipeline.stage.PipelineStage
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectInfo
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycle.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/** Which changes of a project's state observers hear, and when. */
class ProjectLifecycleRegistryTest {

    private val project = FakeProject()
    private val registry = ProjectLifecycleRegistry(project)

    /** Writes down every callback it gets. */
    private class Recorder : ProjectLifecycleObserver.ExplicitObserver {
        val calls = mutableListOf<String>()
        override fun onCompileStart(project: Project) { calls += "compileStart" }
        override fun onCompileEnd(project: Project) { calls += "compileEnd" }
        override fun onEnable(project: Project) { calls += "enable" }
        override fun onDisable(project: Project) { calls += "disable" }
        override fun onDestroy(project: Project) { calls += "destroy" }
    }

    @Test
    fun `an observer registered while compiling isn't told about that compile`() {
        registry.setCurrentState(State.COMPILING)
        val recorder = Recorder()
        registry.registerObserver(recorder)

        assertEquals(emptyList<String>(), recorder.calls)
        registry.setCurrentState(State.DISABLED)
        assertEquals(listOf("compileEnd", "disable"), recorder.calls)
    }

    @Test
    fun `observers hear a change before the change goes on`() {
        val recorder = Recorder()
        registry.registerObserver(recorder, CoroutineScope(Job()))

        registry.setCurrentState(State.COMPILING)
        // The compile's code would run now: the observer has already heard of it.
        assertEquals(listOf("compileStart"), recorder.calls)
    }

    @Test
    fun `no change is skipped when they come quickly`() {
        val recorder = Recorder()
        registry.registerObserver(recorder, CoroutineScope(Job()))

        registry.setCurrentState(State.COMPILING)
        registry.setCurrentState(State.DISABLED)
        registry.setCurrentState(State.COMPILING)
        registry.setCurrentState(State.ENABLED)

        assertEquals(listOf("compileStart", "compileEnd", "disable", "compileStart", "compileEnd", "enable"), recorder.calls)
    }

    @Test
    fun `the lifecycle scope ends after its observers heard of the destroy, and a new one follows`() {
        registry.setCurrentState(State.COMPILING)
        val scope = registry.lifecycleScope
        val recorder = Recorder()
        registry.registerObserver(recorder)

        registry.setCurrentState(State.DESTROYED)

        assertEquals(listOf("compileEnd", "destroy"), recorder.calls)
        assertFalse(scope.isActive)
        val next = registry.lifecycleScope
        assertNotSame(scope, next)
        assertTrue(next.isActive)

        // Observers of the next compile are heard again.
        val again = Recorder()
        registry.registerObserver(again)
        registry.setCurrentState(State.COMPILING)
        assertEquals(listOf("compileStart"), again.calls)
        // The old one went with its scope.
        assertEquals(listOf("compileEnd", "destroy"), recorder.calls)
    }

    @Test
    fun `an observer whose scope ended isn't called`() {
        val scope = CoroutineScope(Job())
        val recorder = Recorder()
        registry.registerObserver(recorder, scope)
        scope.cancel()

        registry.setCurrentState(State.COMPILING)
        assertEquals(emptyList<String>(), recorder.calls)
    }

    @Test
    fun `an observer can unregister itself while it is being called`() {
        val calls = mutableListOf<String>()
        val observer = object : ProjectLifecycleObserver.ExplicitObserver {
            override fun onCompileStart(project: Project) {
                calls += "compileStart"
                registry.unregisterObserver(this)
            }
        }
        registry.registerObserver(observer, CoroutineScope(Job()))

        registry.setCurrentState(State.COMPILING)
        registry.setCurrentState(State.DISABLED)
        registry.setCurrentState(State.COMPILING)
        assertEquals(listOf("compileStart"), calls)
    }

    @Test
    fun `a state observer is told the current state first`() {
        registry.setCurrentState(State.ENABLED)
        val states = mutableListOf<State>()
        registry.registerObserver(ProjectLifecycleObserver.StateObserver { _, state -> states += state }, CoroutineScope(Job()))
        registry.setCurrentState(State.DISABLED)

        assertEquals(listOf(State.ENABLED, State.DISABLED), states)
    }

    /** Only what the registry touches: the project is handed to observers as is. */
    private class FakeProject : Project() {
        override val coroutineContext = EmptyCoroutineContext
        override val directory: File get() = error("unused")
        override val info: ProjectInfo get() = error("unused")
        override val config: MutableConfig get() = error("unused")
        override var threadPoolName: String? = null
        override val isCompiled: Boolean = false
        override val logger: ProjectLogger get() = error("unused")
        override fun getLifecycle(): ProjectLifecycle = error("unused")
        override fun callFunction(name: String, args: Array<out Any>, onException: (Throwable) -> Unit) = error("unused")
        override fun compile(throwException: Boolean): Boolean = error("unused")
        override fun compileAsync(): Flow<Pair<PipelineStage<*, *>, Int>> = error("unused")
        override fun setEnabled(enabled: Boolean): Boolean = error("unused")
        override fun requestUpdate() = error("unused")
        override fun saveInfo() = error("unused")
        override fun loadInfo() = error("unused")
        override fun getLanguage(): Language = error("unused")
        override fun getScope(): Any? = error("unused")
        override fun activeJobs(): Int = error("unused")
        override fun stopAllJobs() = error("unused")
        override fun destroy(requestUpdate: Boolean) = error("unused")
        override fun rename(name: String, preserveMainScript: Boolean) = error("unused")
        override fun getDataDirectory(): File = error("unused")
        override fun isEventCallAllowed(eventId: String): Boolean = error("unused")
        override fun getClassLoader(): ClassLoader = error("unused")
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }
}
