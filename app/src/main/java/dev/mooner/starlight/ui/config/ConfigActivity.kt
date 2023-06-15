package dev.mooner.starlight.ui.config

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dev.mooner.starlight.databinding.ActivityConfigBinding
import dev.mooner.starlight.event.ApplicationEvent
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private val LOG = LoggerFactory.logger {  }

class ConfigActivity : AppCompatActivity() {

    val eventHandleScope: CoroutineScope =
        lifecycleScope

    lateinit var binding: ActivityConfigBinding

    private var recyclerAdapter: ParentRecyclerAdapter? = null
    //private var bypassDestroy: Boolean = false
    lateinit var activityId: String

    lateinit var holder: DataHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindLogNotifier()

        eventHandleScope.launch {
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
        } catch (e: Exception) {
            LOG.error { "Failed to initialize config activity adapter: $e" }
            finish()
        }

        recyclerAdapter = ParentRecyclerAdapter(
            context = binding.root.context,
            configStructure = holder.struct,
            savedData = holder.saved
        ) { parentId, id, view, data ->
            eventHandleScope.launch {
                EventHandler.fireEvent(
                    ApplicationEvent.ConfigActivity.Update(
                        uuid = activityId,
                        data = ApplicationEvent.ConfigActivity.Update.UpdatedData(
                            parentId, id, view, data)
                    )
                )
            }
        }.also(ParentRecyclerAdapter::notifyAllItemInserted)

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
        eventHandleScope.cancel()
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