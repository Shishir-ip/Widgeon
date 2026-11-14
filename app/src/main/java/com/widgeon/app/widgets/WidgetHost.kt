package com.widgeon.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.widgeon.app.MainActivity
import com.widgeon.app.R
import com.widgeon.app.data.WidgetAction
import com.widgeon.app.data.WidgetDesign
import com.widgeon.app.render.DesignRenderer
import com.widgeon.app.render.LiveDataProvider
import com.widgeon.app.widgeon
import kotlin.math.roundToInt

/**
 * Shared logic for every Widgeon widget: renders the design bound to an
 * appWidgetId into a bitmap and pushes it into the widget's RemoteViews.
 */
object WidgetHost {

    /** Which provider class covers which nominal grid footprint. */
    data class Slot(val providerClass: Class<*>, val cols: Int, val rows: Int)

    val SLOTS = listOf(
        Slot(Widget1x1::class.java, 1, 1),
        Slot(Widget2x2::class.java, 2, 2),
        Slot(Widget4x2::class.java, 4, 2),
        Slot(Widget2x4::class.java, 2, 4),
        Slot(Widget4x4::class.java, 4, 4),
    )

    fun allComponentNames(context: Context): List<ComponentName> =
        SLOTS.map { ComponentName(context, it.providerClass) }

    /** Best-matching provider class for a design's grid footprint. */
    fun slotFor(cols: Int, rows: Int): Slot {
        val matches = SLOTS.sortedBy { abs(it.cols - cols) * 10 + abs(it.rows - rows) }
        return matches.first()
    }

    private fun abs(i: Int) = if (i < 0) -i else i

    /** Current widget size in dp (portrait approximation, as launchers report it). */
    fun widgetSizeDp(context: Context, appWidgetId: Int): Pair<Int, Int> {
        val awm = AppWidgetManager.getInstance(context)
        val opts = awm.getAppWidgetOptions(appWidgetId)
        val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val maxH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        if (minW > 0 && maxH > 0) return minW to maxH
        return 110 to 110
    }

    /** Renders the design at [widthDp]x[heightDp] with a safe density cap. */
    fun renderDesign(context: Context, design: WidgetDesign, widthDp: Int, heightDp: Int): Bitmap {
        val density = context.resources.displayMetrics.density.coerceIn(1f, 2.4f)
        val w = (widthDp * density).roundToInt().coerceIn(8, 1600)
        val h = (heightDp * density).roundToInt().coerceIn(8, 1600)
        return DesignRenderer.render(design, w, h, density, LiveDataProvider.snapshot(context))
    }

    fun hasLiveElements(design: WidgetDesign): Boolean = design.elements.any { hasLive(it) }

    private fun hasLive(el: com.widgeon.app.data.Element): Boolean =
        el.type in setOf("clock", "date", "battery", "weather") || el.elements.any { hasLive(it) }

    fun updateWidget(context: Context, appWidgetId: Int) {
        val awm = AppWidgetManager.getInstance(context) ?: return
        if (awm.getAppWidgetInfo(appWidgetId) == null) return

        val designId = context.widgeon.prefs.boundDesignId(appWidgetId)
        val design = designId?.let { runCatching { kotlinx.coroutines.runBlocking { context.widgeon.designRepo.get(it) } }.getOrNull() }

        val views: RemoteViews
        if (design != null) {
            val (wDp, hDp) = widgetSizeDp(context, appWidgetId)
            val bmp = runCatching { renderDesign(context, design, wDp, hDp) }.getOrNull()
            views = RemoteViews(context.packageName, R.layout.widget_root)
            if (bmp != null) {
                views.setViewVisibility(R.id.widget_placeholder, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_image, android.view.View.VISIBLE)
                views.setImageViewBitmap(R.id.widget_image, bmp)
            } else {
                views.setViewVisibility(R.id.widget_placeholder, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_image, android.view.View.GONE)
            }
            views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context, appWidgetId, design))
        } else {
            views = RemoteViews(context.packageName, R.layout.widget_root)
            views.setViewVisibility(R.id.widget_placeholder, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_image, android.view.View.GONE)
            views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context, appWidgetId, null))
        }
        awm.updateAppWidget(appWidgetId, views)
    }

    fun updateAll(context: Context) {
        val awm = AppWidgetManager.getInstance(context) ?: return
        val names = allComponentNames(context)
        names.forEach { name ->
            awm.getAppWidgetIds(name).forEach { updateWidget(context, it) }
        }
        WidgetUpdater.refresh(context)
    }

    private fun tapIntent(context: Context, appWidgetId: Int, design: WidgetDesign?): PendingIntent {
        val action = design?.action ?: findButtonAction(design)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return when {
            action?.type == "openUrl" && !action.url.isNullOrBlank() ->
                PendingIntent.getActivity(
                    context, appWidgetId,
                    Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    flags
                )
            action?.type == "openApp" && !action.appPackage.isNullOrBlank() -> {
                val launch = context.packageManager.getLaunchIntentForPackage(action.appPackage!!)
                    ?: Intent(context, MainActivity::class.java)
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                PendingIntent.getActivity(context, appWidgetId, launch, flags)
            }
            else -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("open_design", design?.id)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                PendingIntent.getActivity(context, appWidgetId, intent, flags)
            }
        }
    }

    private fun findButtonAction(design: WidgetDesign?): WidgetAction? {
        if (design == null) return null
        fun walk(els: List<com.widgeon.app.data.Element>): WidgetAction? {
            for (e in els) {
                if (e.type == "button") e.action?.let { return it }
                walk(e.elements)?.let { return it }
            }
            return null
        }
        return walk(design.elements)
    }

    // ------------------------------------------------------------------ pin

    /** Requests the launcher to pin a widget of the design's size (API 26+). */
    fun requestPin(context: Context, design: WidgetDesign): Boolean {
        val awm = AppWidgetManager.getInstance(context) ?: return false
        if (!awm.isRequestPinAppWidgetSupported) return false
        val slot = slotFor(design.cols, design.rows)
        val extras = Bundle().apply {
            // Hint the picker with the design's nominal size
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, (design.cols * 70 - 30))
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, (design.rows * 70 - 30))
        }
        return awm.requestPinAppWidget(ComponentName(context, slot.providerClass), extras, null)
    }
}
