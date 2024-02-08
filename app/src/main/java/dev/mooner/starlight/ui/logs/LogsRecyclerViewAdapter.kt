package dev.mooner.starlight.ui.logs

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.toSpannable
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.CardLogBinding
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.utils.TimeUtils
import dev.mooner.starlight.plugincore.utils.color
import dev.mooner.starlight.plugincore.utils.isNightMode
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class LogsRecyclerViewAdapter
    : RecyclerView.Adapter<LogsRecyclerViewAdapter.LogsViewHolder>() {

    private val data: MutableList<LogData> = arrayListOf()
    private val visibleData: MutableList<LogData> = arrayListOf()

    var viewType: LogItem.ViewType = LogItem.ViewType.NORMAL
        private set
    private var copyOnLongClick: Boolean = true

    private var filter: ((data: LogData) -> Boolean)? = null

    fun withData(
        list: List<LogData>,
        viewType: LogItem.ViewType = LogItem.ViewType.NORMAL,
        copyOnLongClick: Boolean = true
    ): LogsRecyclerViewAdapter {
        set(list)
        this.viewType = viewType
        this.copyOnLongClick = copyOnLongClick
        return this
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogsViewHolder {
        val binding = CardLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogsViewHolder(binding)
    }

    override fun getItemCount(): Int = visibleData.size

    override fun onBindViewHolder(holder: LogsViewHolder, position: Int) {
        val viewData = visibleData[position]
        holder.bind(viewData)
    }

    fun set(list: List<LogData>, notify: Boolean = false) {
        if (data.isNotEmpty()) {
            val size = visibleData.size
            data.clear()
            visibleData.clear()
            if (notify)
                notifyItemRangeRemoved(0, size)
        }
        data.addAll(list)
        visibleData.addAll(list)
        if (notify)
            notifyItemRangeInserted(0, list.size)
    }

    fun getItems(): List<LogData> =
        data

    fun push(log: LogData, limit: Int = 0) {
        data += log
        if (filter != null && !filter!!.invoke(log))
            return
        if (limit != 0 && visibleData.size >= limit) {
            visibleData.removeAt(0)
            notifyItemRemoved(0)
            visibleData += log
            notifyItemInserted(visibleData.size - 1)
        } else {
            visibleData += log
            notifyItemInserted(visibleData.size - 1)
        }
    }

    fun setViewType(viewType: LogItem.ViewType) {
        this.viewType = viewType
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setFilter(
        types: List<LogType>,
        tags: List<String>,
        content: Pattern?,
    ) {
        filter = { data ->
            if (types.isNotEmpty() && data.type !in types)
                false
            else if (tags.isNotEmpty() && data.tag !in tags)
                false
            else if (content != null && !content.matcher(data.message).matches())
                false
            else
                true
        }
        val nData = data.filter(filter!!)
        visibleData.apply {
            clear()
            addAll(nData)
        }
        notifyDataSetChanged()
    }

    private fun formatDate(millis: Long): String {
        val calendar = Calendar.getInstance()
        val nowDate = calendar.get(Calendar.DAY_OF_YEAR)
        val logDate = calendar.apply { timeInMillis = millis }.get(Calendar.DAY_OF_YEAR)

        return if (nowDate != logDate) {
            fullDateFormat
        } else {
            hourDateFormat
        }.format(millis)
    }

    private fun CardLogBinding.setViewByType(type: LogItem.ViewType) {
        when(type) {
            LogItem.ViewType.NORMAL -> {
                normalModeContent.visibility = View.VISIBLE
                textModeContent.visibility   = View.GONE
            }
            LogItem.ViewType.TEXT -> {
                normalModeContent.visibility = View.GONE
                textModeContent.visibility   = View.VISIBLE
            }
        }
    }

    inner class LogsViewHolder(
        private val binding: CardLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(data: LogData) {
            val context = binding.root.context

            binding.setViewByType(viewType)
            val color = when(data.type) {
                LogType.INFO -> R.color.code_string
                LogType.DEBUG -> R.color.code_purple
                LogType.WARN -> R.color.code_yellow
                LogType.ERROR -> R.color.code_orange
                LogType.CRITICAL -> R.color.code_error
                LogType.VERBOSE -> R.color.monokai_pro_sky
            }.let(context::getColor)
            when(viewType) {
                LogItem.ViewType.NORMAL ->
                    with(binding) {
                        logInfoColor.setCardBackgroundColor(color)
                        logTitleText.text = data.tag ?: data.type.name
                        logContentText.text = data.message
                        logTimeStampText.text = formatDate(data.millis)
                    }
                LogItem.ViewType.TEXT ->
                    with(binding) {
                        val tagColor = if (isNightMode(context))
                            color { "#757575" }
                        else
                            color { "#A3A3A3" }
                        textModeStateIndicator.setBackgroundColor(color)
                        val tag = "%s %s - %s:"
                            .format(data.type.name.substring(0, 1),
                                TimeUtils.formatMillis(data.millis, "HH:mm:ss.SSS"), data.tag)
                        textModeMessage.text = "$tag\n${data.message}"
                        textModeMessage.text.toSpannable().let { span ->
                            span.setSpan(ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            span.setSpan(ForegroundColorSpan(tagColor), 2, tag.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
            }

            if (copyOnLongClick) {
                binding.root.setOnLongClickListener {
                    val clip = ClipData.newPlainText("로그", data.toString())
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(clip)
                    Snackbar.make(it, "로그를 클립보드에 복사했어요.", Snackbar.LENGTH_SHORT).show()
                    true
                }
            }
        }
    }

    companion object {
        @SuppressLint("ConstantLocale")
        private val fullDateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        @SuppressLint("ConstantLocale")
        private val hourDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
}