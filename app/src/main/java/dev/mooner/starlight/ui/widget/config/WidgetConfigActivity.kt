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
import dev.mooner.starlight.R
import dev.mooner.starlight.WIDGET_DEF_STRING
import dev.mooner.starlight.databinding.ActivityWidgetConfigBinding
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.Session.widgetManager
import dev.mooner.starlight.plugincore.config.ConfigStructure
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.widget.Widget
import dev.mooner.starlight.plugincore.widget.WidgetInfo
import dev.mooner.starlight.utils.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

private val logger = LoggerFactory.logger {  }

class WidgetConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding

    var recyclerAdapter: WidgetsThumbnailAdapter? = null
    private val widgets: MutableList<Widget> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        GlobalConfig
            .category("widgets")
            .getString("ids", WIDGET_DEF_STRING)
            .let<_, List<String>>(json::decodeFromString)
            .mapNotNull(widgetManager::getWidgetById)
            .map(Class<out Widget>::newInstance)
            .forEach(widgets::add)

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
            fabAddWidget.setOnClickListener(::onClick)
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
            binding.fabAddWidget -> MaterialDialog(this, BottomSheet(LayoutMode.WRAP_CONTENT))
                .show {
                    setCommonAttrs()
                    maxWidth(res = R.dimen.dialog_width)

                    configStruct(this@WidgetConfigActivity) {
                        struct(getWidgetConfigStructure(::dismiss))
                    }
                }
            binding.leave -> finish()
        }
    }

    private fun addWidget(widget: Class<out Widget>) {
        val instance = widget.newInstance()
        widgets += instance

        recyclerAdapter!!.apply {
            notifyItemInserted(data.size)
            notifyDataEdited(data)
        }
    }

    private fun notifyDataEdited(data: List<Widget>) {
        GlobalConfig.edit {
            category("widgets")["ids"] = Json.encodeToString(data.map { it.id })
        }
        setResult(RESULT_EDITED)
    }

    private fun getWidgetConfigStructure(onDismiss: () -> Unit): ConfigStructure = config {
        val allWidgets = widgetManager.getWidgets()

        for ((category, infos) in widgetManager.getAllWidgetInfo().values.groupBy(WidgetInfo::pluginName)) {
            category {
                id = UUID.randomUUID().toString()
                title = category
                textColor = getColorCompat(R.color.main_bright)
                items {
                    for (info in infos) {
                        val clazz = allWidgets[info.id]
                        if (clazz == null) {
                            logger.warn { "Ignoring widget ${info.id}: class == null" }
                            continue
                        }
                        button {
                            id = info.id
                            title = info.name
                            setOnClickListener { _ ->
                                addWidget(clazz)
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
        widgets.clear()
        recyclerAdapter = null
    }

    companion object {
        const val RESULT_EDITED = 1
    }
}