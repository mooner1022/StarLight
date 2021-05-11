package com.mooner.starlight.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.customview.customView
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession

class Alert {
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "StarLightAlertNotification"
        private var NOTIFICATION_ID = 1
        const val NOTIFICATION = 0
        const val DIALOG = 1
        const val AUTO = 2

        fun show(title: String, msg: String) {
            show(AUTO, title, msg)
        }

        fun show(type: Int, title: String,  msg: String) {
            when(type) {
                NOTIFICATION -> {
                    showNotification(title, msg)
                }
                DIALOG -> {
                    showDialog(title, msg)
                }
                AUTO -> {
                    if (Utils.isForeground()) {
                        showNotification(title, msg)
                    } else {
                        showDialog(title, msg)
                    }
                }
            }
        }

        private fun showDialog(title: String, msg: String) {
            MaterialDialog(MainActivity.windowContext, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                cornerRadius(25f)
                setTitle(title)
                customView(R.layout.dialog_alert)
                cancelOnTouchOutside(false)
                noAutoDismiss()
                positiveButton(text = "확인") {
                    it.dismiss()
                }

                this.findViewById<TextView>(R.id.alertText).text = msg
            }
        }

        private fun showNotification(title: String, msg: String) {
            val manager = ApplicationSession.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createNotificationChannel(manager)
            val style: NotificationCompat.BigTextStyle = NotificationCompat.BigTextStyle()
            with(style) {
                bigText(title)
                setSummaryText(msg)
            }
            val builder = NotificationCompat.Builder(ApplicationSession.context, NOTIFICATION_CHANNEL_ID).apply {
                setSmallIcon(R.mipmap.ic_logo)
                setStyle(style)
            }
            manager.notify(NOTIFICATION_ID, builder.build())
            NOTIFICATION_ID ++
        }

        private fun createNotificationChannel(manager: NotificationManager? = null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationChannel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "알림 채널",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationChannel.enableLights(true)
                notificationChannel.enableVibration(false)

                if (manager == null) {
                    val notificationManager = ApplicationSession.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.createNotificationChannel(notificationChannel)
                } else {
                    manager.createNotificationChannel(notificationChannel)
                }
            }
        }
    }
}