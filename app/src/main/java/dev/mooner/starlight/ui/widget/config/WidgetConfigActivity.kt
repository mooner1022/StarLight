package dev.mooner.starlight.ui.widget.config

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityWidgetConfigBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.Session.widgetManager
import dev.mooner.starlight.plugincore.config.CategoryConfigObject
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.widget.Widget
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.utils.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

class WidgetConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding

    var recyclerAdapter: WidgetsThumbnailAdapter? = null
    private val widgets: MutableList<Widget> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val widgetIds: List<String> = json.decodeFromString(Session.globalConfig.category("widgets").getString("ids", WIDGET_DEF_STRING))
        for (id in widgetIds) {
            with(widgetManager.getWidgetById(id)) {
                if (this != null)
                    widgets += this
            }
        }

        binding.apply {
            subTitle.text = getString(R.string.subtitle_widgets)
                .format(widgets.size)
            scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val alpha = if (scrollY in 0..200) {
                    1f - (scrollY / 200.0f)
                } else {
                    0f
                }
                imageViewLogo.alpha = alpha
                title.alpha = alpha
                subTitle.alpha = alpha
            }
            cardViewAddWidget.setOnClickListener(::onClick)
            leave.setOnClickListener(::onClick)
            recyclerView.setup()
        }
    }

    private fun RecyclerView.setup() {
        recyclerAdapter = WidgetsThumbnailAdapter(binding.root.context) { data ->
            notifyDataEdited(data)
        }.apply {
            data = widgets
            notifyAllItemInserted()
        }

        val dragFlags = when(context.layoutMode) {
            LAYOUT_DEFAULT -> ItemTouchHelper.UP or ItemTouchHelper.DOWN
            LAYOUT_TABLET -> ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            else -> 0
        }

        val itemTouchCallback = object : ItemTouchHelper.SimpleCallback (dragFlags, ItemTouchHelper.RIGHT){
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos: Int = viewHolder.bindingAdapterPosition
                val toPos: Int = target.bindingAdapterPosition
                recyclerAdapter!!.swapData(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                recyclerAdapter!!.removeData(viewHolder.layoutPosition)
            }
        }

        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)

        val mLayoutManager = when(context.layoutMode) {
            LAYOUT_DEFAULT -> LinearLayoutManager(context)
            LAYOUT_TABLET -> StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            else -> LinearLayoutManager(context)
        }

        layoutManager = mLayoutManager
        adapter = recyclerAdapter
    }

    private fun onClick(view: View) {
        when(view) {
            binding.cardViewAddWidget -> MaterialDialog(this, BottomSheet(LayoutMode.WRAP_CONTENT))
                .show {
                    cornerRadius(res = R.dimen.card_radius)
                    maxWidth(res = R.dimen.dialog_width)
                    lifecycleOwner(this@WidgetConfigActivity)
                    customView(R.layout.dialog_logs)

                    val recycler: RecyclerView = findViewById(R.id.rvLog)

                    val configAdapter = ConfigAdapter.Builder(this@WidgetConfigActivity) {
                        bind(recycler)
                        configs { getWidgetConfigList(::dismiss) }
                    }.build()

                    onDismiss {
                        configAdapter.destroy()
                    }
                }
            binding.leave -> finish()
        }
    }

    private fun addWidget(widget: Widget) {
        recyclerAdapter!!.apply {
            data += widget
            notifyItemInserted(data.size)

            notifyDataEdited(data)
        }
    }

    private fun notifyDataEdited(data: List<Widget>) {
        Session.globalConfig.edit {
            category("widgets")["ids"] = Json.encodeToString(data.map { it.id })
        }
        setResult(RESULT_EDITED)
    }

    private fun getWidgetConfigList(onDismiss: () -> Unit): List<CategoryConfigObject> = config {
        val widgets = widgetManager.getWidgets()

        for ((pluginName, _widgets) in widgets) {
            category {
                id = UUID.randomUUID().toString()
                title = pluginName
                textColor = getColorCompat(R.color.main_purple)
                items {
                    for (widget in _widgets) {
                        button {
                            id = widget.id
                            title = widget.name
                            setOnClickListener {
                                addWidget(widget)
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        widgets.forEach(Widget::onDestroyThumbnail)
        recyclerAdapter = null
    }

    companion object {
        const val RESULT_EDITED = 1
    }
}