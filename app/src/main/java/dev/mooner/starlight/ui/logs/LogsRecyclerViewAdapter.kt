package dev.mooner.starlight.ui.logs

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.CardLogBinding
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogType
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

    fun set(list: List<LogData>) {
        if (data.isNotEmpty()) {
            data.clear()
            visibleData.clear()
        }
        data.addAll(list)
        visibleData.addAll(list)
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
        fun bind(data: LogData) {
            val context = binding.root.context

            binding.setViewByType(viewType)
            when(viewType) {
                LogItem.ViewType.NORMAL -> {
                    val color = when(data.type) {
                        LogType.INFO -> R.color.code_string
                        LogType.DEBUG -> R.color.code_purple
                        LogType.WARN -> R.color.code_yellow
                        LogType.ERROR -> R.color.code_orange
                        LogType.CRITICAL -> R.color.code_error
                        LogType.VERBOSE -> R.color.monokai_pro_sky
                    }
                    binding.logInfoColor.setCardBackgroundColor(context.getColor(color))
                    binding.logTitleText.text = data.tag ?: data.type.name
                    binding.logContentText.text = data.message
                    binding.logTimeStampText.text = formatDate(data.millis)
                }
                LogItem.ViewType.TEXT -> {
                    val color = when(data.type) {
                        LogType.INFO -> R.color.code_string
                        LogType.DEBUG -> R.color.code_purple
                        LogType.WARN -> R.color.code_yellow
                        LogType.ERROR -> R.color.code_orange
                        LogType.CRITICAL -> R.color.code_error
                        LogType.VERBOSE -> R.color.monokai_pro_sky
                    }
                    binding.textModeStateIndicator.setBackgroundColor(context.getColor(color))
                    binding.textModeMessage.text = data.toSimpleString()
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