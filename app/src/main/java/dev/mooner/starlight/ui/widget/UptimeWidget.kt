package dev.mooner.starlight.ui.widget

import android.view.View
import android.widget.TextSwitcher
import androidx.core.view.isVisible
import dev.mooner.starlight.R
import dev.mooner.starlight.core.ApplicationSession
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.widget.widget
import dev.mooner.starlight.plugincore.widget.x
import dev.mooner.starlight.utils.formatTime
import kotlin.time.Duration.Companion.seconds

const val WIDGET_UPTIME = "widget_uptime"

val UptimeWidget = widget(WIDGET_UPTIME) {
    name {
        Locale.ENGLISH { "Uptime" }
        Locale.KOREAN  { "업타임" }
    }
    span(default = 4 x 1, min = 2 x 1, max = 8 x 2)

    content {
        if (span.y >= 2) {
            inflate(R.layout.widget_uptime_default)
        } else {
            inflate(R.layout.widget_uptime_slim)
            view<View>(R.id.textView3).isVisible = span.x > 2
        }
        val uptimeText = view<TextSwitcher>(R.id.uptimeText).apply {
            setInAnimation(context, R.anim.text_fade_in)
            setOutAnimation(context, R.anim.text_fade_out)
        }
        ticker(1.seconds) {
            uptimeText.setText(formatTime(System.currentTimeMillis() - ApplicationSession.initMillis))
        }
    }
}
