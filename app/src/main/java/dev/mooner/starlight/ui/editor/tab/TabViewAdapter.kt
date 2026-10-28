package dev.mooner.starlight.ui.editor.tab

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.transform.RoundedCornersTransformation
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.utils.dp
import dev.mooner.starlight.utils.loadWithTint
import kotlinx.coroutines.*
import java.util.*

typealias OnItemEvent = (eventType: Int, index: Int, item: EditorSession) -> Unit

private val LOG = LoggerFactory.logger {  }

class TabViewAdapter(
    private val context: Context,
    val sessions: MutableList<EditorSession>,
    isDark: Boolean,
    private val onItemEvent: OnItemEvent? = null
): RecyclerView.Adapter<TabViewAdapter.TabViewHolder>() {

    private var textColor: Int = getTextColor(isDark)
    private var showFileIcon: Boolean = false
    private var alwaysShowClose: Boolean = true

    private val eventJob: Job = Job()
    private var selectedIndex = UNDEFINED
    private var mRecyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        mRecyclerView = recyclerView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.chip_editor_file, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val session = sessions[position]

        //holder.index = position
        //logger.verbose { "${item.fileName} index= $position" }

        if (!showFileIcon)
            holder.fileIcon.visibility = View.GONE
        else
            session.language.icon
                ?.let { icon ->
                    holder.fileIcon.loadWithTint(icon, null) {
                        transformations(RoundedCornersTransformation(dp(4).toFloat()))
                    }
                }
                ?: holder.fileIcon.loadWithTint(R.drawable.ic_round_code_24, R.color.main_bright)

        holder.fileName.text = getUniqueFileName(session.fileName)
            .let { if (session.isUpdated) "$it *" else it }
        holder.fileName.setTextColor(textColor)
        if (position == selectedIndex)
            holder.indicator.visibility = View.VISIBLE
        else
            holder.indicator.visibility = View.GONE

        holder.closeButton.visibility =
            if (sessions.size > 1 && (alwaysShowClose || position == selectedIndex))
                View.VISIBLE
            else
                View.GONE
        holder.closeButton.imageTintList = ColorStateList.valueOf(textColor)

        holder.closeButton.setOnClickListener {
            val index = sessions.indexOf(session)
            if (index != -1)
                onItemEvent?.invoke(EVENT_CLOSE_REQUESTED, index, session)
        }
    }

    override fun getItemCount(): Int =
        sessions.size

    fun remove(session: EditorSession) {
        val index = sessions.indexOf(session)
        if (index == -1) return

        val wasSelected = index == selectedIndex
        sessions.removeAt(index)
        notifyItemRemoved(index)
        if (index < selectedIndex)
            selectedIndex--
        else if (wasSelected)
            selectedIndex = UNDEFINED

        if (sessions.size == 1)
            notifyItemChanged(0)
        if (wasSelected && sessions.isNotEmpty())
            setSelected((index - 1).coerceAtLeast(0))
        onItemEvent?.invoke(EVENT_CLOSED, index, session)
    }

    fun setSessionContentUpdated(index: Int, isUpdated: Boolean) {
        val session = sessions[index]

        if (session.isUpdated == isUpdated) return
        session.isUpdated = isUpdated
        notifyItemChanged(index)
    }

    fun swapData(fromPos: Int, toPos: Int) {
        Collections.swap(sessions, fromPos, toPos)
        notifyItemMoved(fromPos, toPos)
        LOG.verbose { "from= $fromPos, to= $toPos, selected= $selectedIndex" }
        if (fromPos == selectedIndex)
            selectedIndex = toPos
        else if (toPos == selectedIndex)
            selectedIndex = fromPos
    }

    fun destroy() {
        eventJob.cancel()
        sessions.clear()
        mRecyclerView = null
    }

    val selectedSession: EditorSession?
        get() = sessions.getOrNull(selectedIndex)

    fun setSelected(fileName: String) {
        sessions.indexOfFirst { it.fileName == fileName }.also { idx ->
            if (idx != -1)
                setSelected(idx)
        }
    }

    fun setSelected(index: Int) {
        if (index == selectedIndex) return

        val orgSelection = selectedIndex
        selectedIndex = index

        if (orgSelection != UNDEFINED)
            notifyItemChanged(orgSelection)
        notifyItemChanged(index)
        mRecyclerView?.smoothScrollToPosition(index)

        onItemEvent?.invoke(EVENT_SELECTED, index, sessions[index])
    }

    fun updateTextColor(isDark: Boolean) {
        textColor = getTextColor(isDark)

        notifyItemRangeChanged(0, itemCount)
    }

    private fun getUniqueFileName(path: String): String {
        val parts = path.split("/")
        val fileName = parts.last()
        if (parts.size == 1)
            return fileName
        if (sessions.find { it.fileName != path && it.fileName.endsWith(fileName) } != null)
            return parts.takeLast(2).joinToString("/")
        return fileName
    }

    private fun getTextColor(isDark: Boolean): Int {
        return if (isDark)
            context.getColor(R.color.editor_text_dark)
        else
            context.getColor(R.color.editor_text)
    }

    private suspend fun onGlobalConfigUpdated(event: Events.Config.GlobalConfigUpdate) =
        with(event) {
            LOG.verbose { "Config updated" }
            val category = GlobalConfig.category("e_files")
            val showIcon = category.getBoolean("show_lang_icon", true)
            val showClose = category.getBoolean("always_show_close_button", true)
            if (showFileIcon != showIcon || alwaysShowClose != showClose) {
                showFileIcon = showIcon
                alwaysShowClose = showClose
                withContext(Dispatchers.Main) {
                    notifyItemRangeChanged(0, sessions.size)
                }
            }
        }

    init {
        GlobalConfig.category("e_files").also { category ->
            showFileIcon = category.getBoolean("show_lang_icon", true)
            alwaysShowClose = category.getBoolean("always_show_close_button", true)
        }

        CoroutineScope(Dispatchers.Default + eventJob).launch {
            EventHandler.on(this, ::onGlobalConfigUpdated)
        }
    }

    companion object {
        private const val UNDEFINED = -1

        const val EVENT_SELECTED        = 0
        const val EVENT_CLOSED          = 1
        const val EVENT_CLOSE_REQUESTED = 2
    }

    inner class TabViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        //var index = UNDEFINED

        val fileName: TextView  = itemView.findViewById(R.id.textViewFileName)
        val fileIcon: ImageView = itemView.findViewById(R.id.imageViewIcon)
        val indicator: View     = itemView.findViewById(R.id.isCurrentMain)
        val closeButton: ImageView = itemView.findViewById(R.id.buttonClose)

        init {
            itemView.setOnClickListener {
                setSelected(bindingAdapterPosition)
            }
        }
    }
}