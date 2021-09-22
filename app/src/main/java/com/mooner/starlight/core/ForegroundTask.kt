package com.mooner.starlight.core

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.logger.Logger
import kotlin.system.exitProcess

class ForegroundTask: Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "ForegroundServiceChannel"
        var isRunning: Boolean = false
    }

    private var windowManager: WindowManager? = null
    private var view: View? = null

    @SuppressLint("InflateParams")
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { paramThread, paramThrowable ->
            Logger.wtf("StarLight-core", """
                An uncaught critical exception occurred, shutting down...
                [버그 제보시 아래 메세지를 첨부해주세요.]
                ──────────
                thread  : ${paramThread.name}
                message : ${paramThrowable.message}
                cause   : ${paramThrowable.cause}
                ┉┉┉┉┉┉┉┉┉┉
                stackTrace:
            """.trimIndent() + paramThrowable.stackTraceToString() + "\n──────────")
            paramThrowable.printStackTrace()
            exitProcess(2)
        }

        if (!ApplicationSession.isInitComplete) {
            ApplicationSession.context = applicationContext
            ApplicationSession.init({},{})
        }

        createNotificationChannel()
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }
        val style: NotificationCompat.BigTextStyle = NotificationCompat.BigTextStyle()
        style.bigText("당신만을 위한 봇들을 관리중이에요 :)")
        style.setSummaryText("StarLight 실행중")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setStyle(style)
                .setSmallIcon(R.mipmap.ic_logo)
                .setContentIntent(pendingIntent)
                .setShowWhen(false)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } else {
            val notification = NotificationCompat.Builder(this)
                .setStyle(style)
                .setSmallIcon(R.mipmap.ic_logo)
                .setContentIntent(pendingIntent)
                .setShowWhen(false)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        }

        /*
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT
            )
        } else {
            WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
        }
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        view = inflater.inflate(R.layout.layout_overlay_dialog, null)
        val logsRecycler: RecyclerView = view!!.findViewById(R.id.recyclerViewLogs)
        val logs = Logger.filterNot(LogType.DEBUG)
        val logsAdapter = LogsRecyclerViewAdapter(applicationContext)
        if (logs.isNotEmpty()) {
            val recyclerLayoutManager = LinearLayoutManager(applicationContext).apply {
                reverseLayout = true
                stackFromEnd = true
            }
            logsAdapter.data = logs.toMutableList()
            with(logsRecycler) {
                itemAnimator = FadeInLeftAnimator()
                layoutManager = recyclerLayoutManager
                adapter = logsAdapter
            }
            logsAdapter.notifyItemRangeInserted(0, logs.size)
        }

        windowManager!!.addView(view, params)
        */
    }

    override fun onDestroy() {
        super.onDestroy()
        if (windowManager != null) {
            if (view != null) {
                windowManager!!.removeView(view)
                view = null
            }
            windowManager = null
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                "포그라운드 채널",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            //notificationChannel.enableLights(false)
            //notificationChannel.enableVibration(false)
            //notificationChannel.description = "StarLight 의 서비스를 실행하기 위한 알림이에요."

            val notificationManager = applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(
                notificationChannel)
        }
    }
}