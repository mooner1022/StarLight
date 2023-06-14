/*
 * LogGenWidget.kt created by Minki Moon(mooner1022) on 23. 3. 14. 오후 5:22
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.widget

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import com.google.android.flexbox.FlexboxLayout
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.utils.draw
import dev.mooner.starlight.plugincore.widget.Widget
import dev.mooner.starlight.plugincore.widget.WidgetSize

private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

private val logger = LoggerFactory.logger {  }

class LogGenWidget: Widget() {

    private var notify: Boolean = false

    override val id: String = "log_generator"

    override val name: String = "로그 생성 위젯"

    override val size: WidgetSize = WidgetSize.Wrap

    override fun onCreateWidget(view: View) {
        drawWidget(view) { _, type ->
            if (notify && type == LogType.INFO) {
                logger.info(LogData.FLAG_NOTIFY) {
                    "${type.name} log from LogGenWidget, notify= true"
                }
                return@drawWidget
            }
            logger.log(type) {
                "${type.name} log from LogGenWidget"
            }
        }
    }

    override fun onCreateThumbnail(view: View) {
        drawWidget(view) { _, _ -> }
    }

    private fun drawWidget(view: View, onClick: (view: View, type: LogType) -> Unit) {
        val context = view.context

        draw(view) {
            FlexboxLayout(context).place(
                width = MATCH_PARENT,
                height = WRAP_CONTENT
            ) {
                params {
                    setPadding(0, dp(10), 0, dp(30))
                }

                for (type in LogType.values().sortedBy(LogType::priority)) {
                    Button(context).apply {
                        setOnClickListener {
                            onClick(it, type)
                        }
                        text = type.name.substring(0, 1)
                        params {
                            height = WRAP_CONTENT
                            width = WRAP_CONTENT
                        }
                    }.also(::addView)
                }
            }
            Switch(context).place(
                width = WRAP_CONTENT,
                height = WRAP_CONTENT
            ) {
                params {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                isChecked = false
                text = "NOTIFY"
                setOnCheckedChangeListener { _, isChecked ->
                    notify = isChecked
                }
            }
        }
        view.updateHeight()
    }
}