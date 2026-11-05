package dev.mooner.starlight.ui.crash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.mooner.starlight.R
import dev.mooner.starlight.core.CrashGuard
import dev.mooner.starlight.utils.canPostNotification

/** Tells the user that the app restarted after a crash, without stopping it the way a blocked start does. */
internal object CrashNotifier {

    private const val CHANNEL = "crash_report"
    private const val NOTIFICATION_ID = 0x5C1

    fun notifyRecovered(context: Context, crash: CrashGuard.Crash) {
        if (!context.canPostNotification())
            return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.crash_channel), NotificationManager.IMPORTANCE_DEFAULT))

        val details = PendingIntent.getActivity(context, 0,
            FatalErrorActivity.intent(context, crash.report, blocked = false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val share = PendingIntent.getActivity(context, 1,
            Intent.createChooser(FatalErrorActivity.shareIntent(crash.report), context.getString(R.string.crash_share_title))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.crash_recovered_title))
            .setContentText(crash.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(crash.summary))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(details)
            .addAction(0, context.getString(R.string.crash_share), share)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
