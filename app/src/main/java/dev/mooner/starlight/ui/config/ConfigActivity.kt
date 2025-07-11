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

        bindLogNotifier()

        eventHandlerScope.launch {
            EventHandler.on(this, ::onDestroyCall)
        }

        val title = intent.getStringExtra(EXTRA_TITLE)
        val subTitle = intent.getStringExtra(EXTRA_SUBTITLE)

        binding.title.text = title
        binding.subTitle.text = subTitle
        binding.scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val alpha = if (scrollY in 0..200) {
                1f - (scrollY / 200.0f)
            } else {
                0f
            }
            binding.imageViewLogo.alpha = alpha
            binding.title.alpha = alpha
            binding.subTitle.alpha = alpha
        }

        binding.leave.setOnClickListener { finish() }

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

    /*
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Bypass onDestroyed()
        //Logger.v("onSaveInstanceState called, bypassing onDestroyed() call on ID: $activityId")
        //bypassDestroy = true
        //onDestroyed()
    }
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        onPaused()
    }

    override fun onDestroy() {
        EventHandler.fireEventWithScope(
            ApplicationEvent.ConfigActivity.Destroy(
                uuid = activityId
            )
        )
        eventHandlerScope.cancel()
        recyclerAdapter?.destroy()
        recyclerAdapter = null
        onDestroyed()
        super.onDestroy()
        //if (bypassDestroy) {
        //    bypassDestroy = false
        //    return
        //}
        //onDestroyed()
    }

    private fun onDestroyCall(event: ApplicationEvent.ConfigActivity.Destroy) {
        if (event.uuid == activityId) {
            LOG.verbose { "Finishing config activity with ID: ${event.uuid}" }
            finish()
        }
    }
}