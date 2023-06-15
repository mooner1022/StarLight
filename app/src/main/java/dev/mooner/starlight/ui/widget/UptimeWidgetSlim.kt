package dev.mooner.starlight.ui.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextSwitcher
import dev.mooner.starlight.R
import dev.mooner.starlight.core.ApplicationSession
import dev.mooner.starlight.plugincore.widget.Widget
import dev.mooner.starlight.plugincore.widget.WidgetSize
import dev.mooner.starlight.utils.formatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class UptimeWidgetSlim: Widget() {

    override val id: String = "widget_uptime_slim"
    override val name: String = "업타임(슬림)"
    override val size: WidgetSize = WidgetSize.Slim

    private var isCreated = false
    private var uptimeText: TextSwitcher? = null
    private var uptimeTimer: Timer? = null
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val updateUpTimeTask: TimerTask
        get() = object: TimerTask() {
            override fun run() {
                if (uptimeText == null) {
                    cancelTimer()
                    return
                }
                val diffMillis = System.currentTimeMillis() - ApplicationSession.initMillis
                mainScope.launch {
                    uptimeText?.setText(formatTime(diffMillis))
                }
            }
        }


    override fun onCreateWidget(view: View) {
        if (uptimeTimer != null) return
        LayoutInflater.from(view.context).inflate(R.layout.widget_uptime_slim, view as ViewGroup, true)
        with(view) {
            uptimeText = findViewById(R.id.uptimeText)
            uptimeText!!.setInAnimation(context, R.anim.text_fade_in)
            uptimeText!!.setOutAnimation(context, R.anim.text_fade_out)
        }
        scheduleTimer()
        isCreated = true
    }

    override fun onResumeWidget() {
        super.onResumeWidget()
        if (!isCreated) return
        scheduleTimer()
    }

    override fun onPauseWidget() {
        super.onPauseWidget()
        cancelTimer()
    }

    override fun onDestroyWidget() {
        super.onDestroyWidget()
        cancelTimer()
        uptimeText = null
        isCreated = false
    }

    private fun scheduleTimer() {
        if (uptimeTimer == null) {
            uptimeTimer = Timer()
            uptimeTimer!!.schedule(updateUpTimeTask, 0, 1000)
        }
    }

    private fun cancelTimer() {
        if (uptimeTimer != null) {
            uptimeTimer!!.cancel()
            uptimeTimer = null
        }
    }

    override fun onCreateThumbnail(view: View) {
        LayoutInflater.from(view.context)
            .inflate(R.layout.widget_uptime_slim, view as ViewGroup, true)
    }
}