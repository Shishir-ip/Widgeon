package com.widgeon.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.widgeon.app.MainActivity
import com.widgeon.app.R
import com.widgeon.app.widgeon

object Notifier {

    const val CH_PROGRESS = "widgeon_progress"
    const val CH_RESULT = "widgeon_result"
    const val ID_PROGRESS = 4001
    const val ID_RESULT_BASE = 4100

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, context.getString(R.string.notif_channel_progress), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.notif_channel_progress_desc)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, context.getString(R.string.notif_channel_result), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notif_channel_result_desc)
            }
        )
    }

    fun canPost(context: Context): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && context.widgeon.prefs.notifEnabled
    }

    private fun openAppIntent(context: Context, designId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_design", designId)
        }
        return PendingIntent.getActivity(
            context, designId?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildProgress(context: Context, stage: String, percent: Int): android.app.Notification =
        NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_widgeon_fg)
            .setContentTitle("Designing your widget…")
            .setContentText(stage)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context, null))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun progress(context: Context, stage: String, percent: Int) {
        if (!canPost(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(ID_PROGRESS, buildProgress(context, stage, percent)) }
    }

    fun success(context: Context, designId: String, name: String) {
        NotificationManagerCompat.from(context).cancel(ID_PROGRESS)
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_RESULT)
            .setSmallIcon(R.drawable.ic_widgeon_fg)
            .setContentTitle("“$name” is ready")
            .setContentText("Tap to view, edit or add it to your home screen.")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, designId))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_RESULT_BASE + designId.hashCode(), n) }
    }

    fun failure(context: Context, message: String) {
        NotificationManagerCompat.from(context).cancel(ID_PROGRESS)
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_RESULT)
            .setSmallIcon(R.drawable.ic_widgeon_fg)
            .setContentTitle("Widget generation failed")
            .setContentText(message.take(180))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.take(400)))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, null))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_RESULT_BASE + 999, n) }
    }

    fun cancelProgress(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_PROGRESS)
    }
}
