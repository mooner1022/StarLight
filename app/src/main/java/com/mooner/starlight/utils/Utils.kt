package com.mooner.starlight.utils

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.plugincore.logger.LogData
import com.mooner.starlight.plugincore.logger.LogType
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import com.mooner.starlight.ui.splash.SplashActivity
import jp.wasabeef.recyclerview.animators.FadeInLeftAnimator
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class Utils {
    companion object {

        private val alphanumericPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        fun randomAlphanumeric(length: Int): String {
            val arr: ArrayList<Char> = arrayListOf()
            repeat(length) {
                arr.add(alphanumericPool.random())
            }
            return arr.joinToString("")
        }

        fun isForeground(): Boolean {
            val appProcessInfo = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(appProcessInfo)
            return appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE
        }

        fun formatStringRes(@StringRes id: Int, map: Map<String, String>): String {
            var string = ApplicationSession.context.getString(id)
            for (pair in map) {
                string = string.replace("\$${pair.key}", pair.value)
            }
            return string
        }

        fun String.trimLength(max: Int): String {
            return if (this.length <= max) this else (this.substring(0, max) + "..")
        }

        fun checkPermissions(context: Context, permissions: Array<String>): Boolean {
            for (permission in permissions) {
                val perm = ContextCompat.checkSelfPermission(context, permission)
                if (perm == PackageManager.PERMISSION_DENIED) return false
            }
            return true
        }

        fun formatTime(millis: Long): String {
            val day = 1000 * 60 * 60 * 24
            val s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
            val m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
            val h = TimeUnit.MILLISECONDS.toHours(millis) % 24
            return if (millis >= day) {
                val d = TimeUnit.MILLISECONDS.toDays(millis)
                String.format("%d일 %d시간 %02d분 %02d초", d, h, m, s)
            } else {
                String.format("%d시간 %02d분 %02d초", h, m, s)
            }
        }

        fun restartApplication(context: Context) {
            val mStartActivity = Intent(context, SplashActivity::class.java)
            val mPendingIntent = PendingIntent.getActivity(
                context,
                0,
                mStartActivity,
                PendingIntent.FLAG_CANCEL_CURRENT
            )
            val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            mgr[AlarmManager.RTC, System.currentTimeMillis() + 100] = mPendingIntent
            exitProcess(0)
        }

        fun showLogsDialog(context: Context, logs: List<LogData>? = null): MaterialDialog {
            val mLogs = logs?: Logger.filterNot(LogType.DEBUG)
            val mAdapter = LogsRecyclerViewAdapter(context).apply {
                data = mLogs.toMutableList()
            }
            val mLayoutManager = LinearLayoutManager(context).apply {
                reverseLayout = true
                stackFromEnd = true
            }
            return MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                cornerRadius(25f)
                cancelOnTouchOutside(true)
                noAutoDismiss()
                title(text = context.getString(R.string.title_logs))
                customView(R.layout.dialog_logs)
                positiveButton(text = context.getString(R.string.close)) {
                    dismiss()
                }

                val recycler: RecyclerView = findViewById(R.id.rvLog)
                recycler.apply {
                    itemAnimator = FadeInLeftAnimator()
                    layoutManager = mLayoutManager
                    adapter = mAdapter
                }
                mAdapter.notifyItemRangeInserted(0, mLogs.size)

                if (logs == null) {
                    val key = randomAlphanumeric(8)
                    Logger.bindListener(key) {
                        mAdapter.pushLog(it)
                    }
                    onDismiss {
                        Logger.unbindListener(key)
                    }
                }
            }
        }
    }
}