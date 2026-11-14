package com.widgeon.app.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.widgeon.app.widgeon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps live widgets (clock/date/battery/weather) fresh with a minute-level
 * repeating alarm while at least one such widget is bound, and refreshes the
 * weather cache underneath.
 */
object WidgetUpdater {

    const val ACTION_REFRESH = "com.widgeon.app.action.REFRESH_WIDGETS"
    private const val MINUTE_MS = 60_000L

    fun refresh(context: Context) {
        val prefs = context.widgeon.prefs
        val anyLive = prefs.allBindings().keys.isNotEmpty()
        if (anyLive) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pendingIntent(context)
        // Inexact repeating needs no special permission; ~1 minute cadence.
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + MINUTE_MS, MINUTE_MS, pi)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BootReceiver::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            context, 9001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Handles alarm ticks, boot/replacement, timezone/time changes. */
class BootReceiver : BroadcastReceiver() {

    private var scope: CoroutineScope? = null

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        s.launch {
            try {
                when (intent.action) {
                    WidgetUpdater.ACTION_REFRESH, Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                        if (intent.action == WidgetUpdater.ACTION_REFRESH &&
                            com.widgeon.app.render.WeatherRepository.isStale(context)
                        ) {
                            runCatching { com.widgeon.app.render.WeatherRepository.refresh(context) }
                        }
                        WidgetHost.updateAll(context)
                    }
                }
            } finally {
                result.finish()
                s.cancel()
            }
        }
    }
}
