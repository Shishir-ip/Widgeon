package com.widgeon.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.widgeon.app.MainActivity
import com.widgeon.app.R
import com.widgeon.app.data.ImageStore
import com.widgeon.app.data.WidgetAction
import com.widgeon.app.data.WidgetDesign
import com.widgeon.app.render.DesignRenderer
import com.widgeon.app.render.LiveDataProvider
import com.widgeon.app.widgeon
import kotlin.math.roundToInt

/**
 * Shared logic for every Widgeon widget: renders the design bound to an
 * appWidgetId and pushes it into the widget's RemoteViews.
 *
 * Live strategy (v1.1): clock/date elements become real TextClock overlays on
 * Android 12+ — the launcher updates them every minute with zero alarms, even
 * when the app process is dead. On older versions (and for battery/weather),
 * the content is embedded in the bitmap and refreshed by WidgetUpdater.
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

    private val LIVE_IDS = intArrayOf(R.id.live_0, R.id.live_1, R.id.live_2, R.id.live_3)

    fun allComponentNames(context: Context): List<ComponentName> =
        SLOTS.map { ComponentName(context, it.providerClass) }

    /** Best-matching provider class for a design's grid footprint. */
    fun slotFor(cols: Int, rows: Int): Slot {
        val matches = SLOTS.sortedBy { abs(it.cols - cols) * 10 + abs(it.rows - rows) }
        return matches.first()
    }

    fun slotForClassName(className: String?): Slot? =
        SLOTS.firstOrNull { it.providerClass.name == className }

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
        return DesignRenderer.render(
            design, w, h, density,
            LiveDataProvider.snapshot(context),
            imageLoader = ImageStore.loader(context),
        )
    }

    fun hasLiveElements(design: WidgetDesign): Boolean = design.elements.any { hasLive(it) }

    private fun hasLive(el: com.widgeon.app.data.Element): Boolean =
        el.type in setOf("clock", "date", "battery", "weather") || el.elements.any { hasLive(it) }

    fun updateWidget(context: Context, appWidgetId: Int) {
        val awm = AppWidgetManager.getInstance(context) ?: return
        if (awm.getAppWidgetInfo(appWidgetId) == null) return

        val designId = context.widgeon.prefs.boundDesignId(appWidgetId)
        val design = designId?.let { runCatching { kotlinx.coroutines.runBlocking { context.widgeon.designRepo.get(it) } }.getOrNull() }

        val views: RemoteViews = if (design != null) {
            val (wDp, hDp) = widgetSizeDp(context, appWidgetId)
            buildDesignViews(context, design, wDp, hDp)
        } else {
            placeholderViews(context)
        }
        awm.updateAppWidget(appWidgetId, views)
    }

    fun buildDesignViews(context: Context, design: WidgetDesign, wDp: Int, hDp: Int): RemoteViews {
        // Overlays use only officially-annotated RemoteViews actions. If the
        // user toggles compatibility mode, everything is baked into the bitmap.
        val useOverlay = Build.VERSION.SDK_INT >= 31 && context.widgeon.prefs.overlayClock
        val density = context.resources.displayMetrics.density.coerceIn(1f, 2.4f)
        val w = (wDp * density).roundToInt().coerceIn(8, 1600)
        val h = (hDp * density).roundToInt().coerceIn(8, 1600)

        val result = DesignRenderer.renderDetailed(
            design, w, h, density,
            LiveDataProvider.snapshot(context),
            includeLive = !useOverlay,
            imageLoader = ImageStore.loader(context),
        )

        val views = RemoteViews(context.packageName, R.layout.widget_root)
        views.setViewVisibility(R.id.widget_image, View.VISIBLE)
        views.setImageViewBitmap(R.id.widget_image, result.bitmap)
        views.setViewVisibility(R.id.widget_placeholder, View.GONE)
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context, design))

        // Hide all overlay slots first
        LIVE_IDS.forEach { views.setViewVisibility(it, View.GONE) }

        if (useOverlay) {
            result.overlays.forEachIndexed { i, spec ->
                val id = LIVE_IDS[i]
                views.setViewVisibility(id, View.VISIBLE)
                // TextClock format setters are explicitly @RemotableViewMethod
                views.setCharSequence(id, "setFormat12Hour", spec.pattern)
                views.setCharSequence(id, "setFormat24Hour", spec.pattern)
                // Official API 31 styling methods (annotated, no reflection)
                views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, spec.fontSizeDp)
                views.setViewLayoutWidth(id, spec.widthDp, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutMargin(id, RemoteViews.MARGIN_LEFT, spec.leftDp, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutMargin(id, RemoteViews.MARGIN_TOP, spec.topDp, TypedValue.COMPLEX_UNIT_DIP)
                // TextView.setTextColor / setGravity are @RemotableViewMethod
                views.setInt(id, "setTextColor", spec.color)
                views.setInt(id, "setGravity", Gravity.CENTER_HORIZONTAL or Gravity.TOP)
            }
        }
        return views
    }

    private fun placeholderViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_root)
        views.setViewVisibility(R.id.widget_image, View.GONE)
        views.setViewVisibility(R.id.widget_placeholder, View.VISIBLE)
        // Tapping an unbound widget opens the design chooser directly
        val choose = Intent(context, WidgetConfigActivity::class.java)
            .putExtra("auto_pick_unbound", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(context, 7003, choose, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        return views
    }

    fun updateAll(context: Context) {
        val awm = AppWidgetManager.getInstance(context) ?: return
        allComponentNames(context).forEach { name ->
            awm.getAppWidgetIds(name).forEach { updateWidget(context, it) }
        }
        WidgetUpdater.refresh(context)
    }

    private fun tapIntent(context: Context, design: WidgetDesign?): PendingIntent {
        val action = design?.action ?: findButtonAction(design)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return when {
            action?.type == "openUrl" && !action.url.isNullOrBlank() ->
                PendingIntent.getActivity(
                    context, appWidgetHash(design),
                    Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    flags
                )
            action?.type == "openApp" && !action.appPackage.isNullOrBlank() -> {
                val launch = context.packageManager.getLaunchIntentForPackage(action.appPackage!!)
                    ?: Intent(context, MainActivity::class.java)
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                PendingIntent.getActivity(context, appWidgetHash(design), launch, flags)
            }
            else -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("open_design", design?.id)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                PendingIntent.getActivity(context, appWidgetHash(design), intent, flags)
            }
        }
    }

    private fun appWidgetHash(design: WidgetDesign?): Int = design?.id?.hashCode() ?: 0

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

    /**
     * Requests the launcher to pin a widget of the design's size (API 26+).
     * On success the launcher opens the design chooser pre-targeted at the
     * freshly created widget, so it never stays a placeholder.
     */
    fun requestPin(context: Context, design: WidgetDesign): Boolean {
        val awm = AppWidgetManager.getInstance(context) ?: return false
        if (!awm.isRequestPinAppWidgetSupported) return false
        val slot = slotFor(design.cols, design.rows)
        val extras = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, (design.cols * 70 - 30))
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, (design.rows * 70 - 30))
        }
        val chooseDesign = Intent(context, WidgetConfigActivity::class.java)
            .putExtra("auto_pick_unbound", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val success = PendingIntent.getActivity(
            context, 7002, chooseDesign,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return awm.requestPinAppWidget(ComponentName(context, slot.providerClass), extras, success)
    }
}
