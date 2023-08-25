/*
 * LogItem.kt created by Minki Moon(mooner1022) on 1/24/23, 1:43 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.logs

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.fastadapter.binding.AbstractBindingItem
import dev.mooner.starlight.ID_VIEW_ITEM_LOG
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.CardLogBinding
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogType
import java.text.SimpleDateFormat
import java.util.*

class LogItem: AbstractBindingItem<CardLogBinding>() {

    var logData: LogData? = null
    var viewType: ViewType = ViewType.NORMAL
        private set
    private var copyOnLongClick: Boolean = true

    fun withLogData(data: LogData, viewType: ViewType, copyOnLongClick: Boolean = true): LogItem {
        logData = data
        this.viewType = viewType
        this.copyOnLongClick = copyOnLongClick
        return this
    }

    override val type: Int = ID_VIEW_ITEM_LOG

    override fun bindView(binding: CardLogBinding, payloads: List<Any>) {
        val data = logData!!
        val context = binding.root.context

        binding.setViewByType(viewType)
        when(viewType) {
            ViewType.NORMAL -> {
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
            ViewType.TEXT -> {
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

    private fun CardLogBinding.setViewByType(type: ViewType) {
        when(type) {
            ViewType.NORMAL -> {
                normalModeContent.visibility = View.VISIBLE
                textModeContent.visibility   = View.GONE
            }
            ViewType.TEXT -> {
                normalModeContent.visibility = View.GONE
                textModeContent.visibility   = View.VISIBLE
            }
        }
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

    override fun equals(other: Any?): Boolean {
        return other is LogItem && logData?.equals(other.logData) == true
    }

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): CardLogBinding =
        CardLogBinding.inflate(inflater, parent, false)

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (logData?.hashCode() ?: 0)
        return result
    }

    companion object {
        @SuppressLint("ConstantLocale")
        private val fullDateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        @SuppressLint("ConstantLocale")
        private val hourDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    enum class ViewType {
        NORMAL,
        TEXT,
    }
}