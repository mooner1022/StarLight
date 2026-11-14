package dev.mooner.starlight.ui.config

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dev.mooner.configdsl.ConfigOption
import dev.mooner.configdsl.adapters.ParentConfigAdapter
import dev.mooner.starlight.databinding.ActivityConfigBinding
import dev.mooner.starlight.event.ApplicationEvent
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.utils.*
import dev.mooner.starlight.utils.bindPageHeader
import dev.mooner.starlight.utils.fitSkyPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val LOG = LoggerFactory.logger {  }

class ConfigActivity : AppCompatActivity() {

    val eventHandlerScope: CoroutineScope =
        lifecycleScope

    lateinit var binding: ActivityConfigBinding

    private var recyclerAdapter: ParentConfigAdapter? = null
    //private var bypassDestroy: Boolean = false
    lateinit var activityId: String

    lateinit var holder: DataHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitSkyPage(binding.root, binding.skyHeader, binding.scroll, binding.leave)
        bindPageHeader(binding.scroll, binding.skyHeader, binding.title, binding.subTitle)

        bindLogNotifier()

        eventHandlerScope.launch {
            EventHandler.on(this, ::onDestroyCall)
        }

        val title = intent.getStringExtra(EXTRA_TITLE)
        val subTitle = intent.getStringExtra(EXTRA_SUBTITLE)

        binding.title.text = title
        binding.subTitle.text = subTitle

        binding.leave.setOnClickListener { finish() }

        // Brought back in a new process: what the screen showed went with the old one, and it can only close.
        if (!hasConfigHolder(intent.getStringExtra(EXTRA_ACTIVITY_ID).orEmpty())) {
            LOG.verbose { "A settings screen whose contents are gone was brought back; closing it" }
            activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID).orEmpty()
            finish()
            return
        }
        try {
            initAdapter()
            if (!this::holder.isInitialized)
                throw IllegalStateException("Holder not initialized")
        } catch (e: Exception) {
            LOG.error { "Failed to initialize config activity adapter: $e" }
            finish()
            return
        }
        runBlocking(lifecycleScope.coroutineContext) {
            EventHandler.fireEvent(ApplicationEvent.ConfigActivity.Create(
                uuid           = activityId,
                activity       = this@ConfigActivity,
                coroutineScope = this,
            ))
        }

        val struct = holder.structBlock.invoke(this)
        recyclerAdapter = ParentConfigAdapter(
            configStructure  = struct,
            configData       = holder.saved,
            coroutineContext = lifecycleScope.coroutineContext
        ).also(ParentConfigAdapter::notifyAllItemInserted).apply {
            eventFlow
                .filterIsInstance<ConfigOption.RootUpdateData>()
                .onEach { data ->
                    EventHandler.fireEvent(
                        ApplicationEvent.ConfigActivity.Update(
                            uuid = activityId,
                            data = ApplicationEvent.ConfigActivity.Update.UpdatedData(
                                data.rootId,
                                data.provider,
                                data.data,
                                data.jsonData,
                            )
                        )
                    )
                }
                .launchIn(eventHandlerScope)
        }

        val mLayoutManager = LinearLayoutManager(applicationContext)
        binding.recyclerView.apply {
            layoutManager = mLayoutManager
            adapter = recyclerAdapter
        }
    }

    override fun onDestroy() {
        // Only a screen that is closed tells so and lets go of what it was made of. One Android takes down to make
        // anew, on a rotation or a theme change, comes back with the same id: telling of its end would close the
        // new one, and without its holder the new one couldn't be made at all.
        if (isFinishing) {
            EventHandler.fireEventWithScope(
                ApplicationEvent.ConfigActivity.Destroy(
                    uuid = activityId
                )
            )
            onClosed()
        }
        eventHandlerScope.cancel()
        recyclerAdapter?.destroy()
        recyclerAdapter = null
        super.onDestroy()
    }

    private fun onDestroyCall(event: ApplicationEvent.ConfigActivity.Destroy) {
        if (event.uuid == activityId) {
            LOG.verbose { "Finishing config activity with ID: ${event.uuid}" }
            finish()
        }
    }
}