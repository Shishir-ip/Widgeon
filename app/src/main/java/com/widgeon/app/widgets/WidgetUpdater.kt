package com.widgeon.app.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.widgeon.app.data.WidgetDesign
import com.widgeon.app.widgeon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Refresh strategy:
 *  - Clock/date: rendered as launcher-side TextClock overlays on Android 12+,
 *    so they tick with zero involvement from us. Below Android 12 they are
 *    baked into the bitmap and need a 1-minute refresh.
 *  - Battery/weather: baked into the bitmap → 15-minute refresh.
 *  - Rotating photo slideshows: refresh every 60s (system minimum for alarms).
 * The fastest required cadence wins.
 */
object WidgetUpdater {

    const val ACTION_REFRESH = "com.widgeon.app.action.REFRESH_WIDGETS"
    private const val MINUTE_MS = 60_000L
    private const val DATA_MS = 15L * 60 * 1000

    fun refresh(context: Context) {
        val prefs = context.widgeon.prefs
        val designs = prefs.allBindings().values.mapNotNull { id ->
            runCatching { kotlinx.coroutines.runBlocking { context.widgeon.designRepo.get(id) } }.getOrNull()
        }
        val interval = requiredInterval(designs)
        if (interval == null) cancel(context) else schedule(context, interval)
    }

    /** Returns the required refresh interval in ms, or null if none is needed. */
    fun requiredInterval(designs: List<WidgetDesign>): Long? {
        var needed: Long? = null
        fun take(ms: Long) { needed = minOf(needed ?: ms, ms) }
        val belowTwelve = Build.VERSION.SDK_INT < 31
        for (d in designs) {
            if (d.hasRotatingImages()) take(MINUTE_MS)
            if (belowTwelve && hasSystemLive(d)) take(MINUTE_MS)
            if (hasDataElements(d)) take(DATA_MS)
        }
        return needed
    }

    private fun hasSystemLive(d: WidgetDesign): Boolean =
        d.elements.any { it.type in com.widgeon.app.data.ElementTypes.systemLive }

    fun hasDataElements(d: WidgetDesign): Boolean = d.elements.any { hasData(it) }

    private fun hasData(el: com.widgeon.app.data.Element): Boolean =
        el.type in setOf("battery", "weather") || el.elements.any { hasData(it) }

    fun schedule(context: Context, intervalMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pendingIntent(context)
        am.cancel(pi)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + intervalMs, intervalMs, pi)
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

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        s.launch {
            try {
                when (intent.action) {
                    WidgetUpdater.ACTION_REFRESH, Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                        if (intent.action == WidgetUpdater.ACTION_REFRESH) {
                            // Advance photo slideshows, if any bound widget rotates images
                            val prefs = context.widgeon.prefs
                            val designs = prefs.allBindings().values.mapNotNull { id ->
                                runCatching { kotlinx.coroutines.runBlocking { context.widgeon.designRepo.get(id) } }.getOrNull()
                            }
                            if (designs.any { it.hasRotatingImages() }) {
                                prefs.rotationCounter = prefs.rotationCounter + 1
                            }
                            if (com.widgeon.app.render.WeatherRepository.isStale(context) &&
                                designs.any { WidgetUpdater.hasDataElements(it) }
                            ) {
                                runCatching { com.widgeon.app.render.WeatherRepository.refresh(context) }
                            }
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
