package com.widgeon.app.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.widgeon.app.widgeon

/**
 * Base for all Widgeon widget providers. Subclasses exist only so each grid
 * size gets its own picker entry with its own AppWidgetProviderInfo.
 */
abstract class WidgeonWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetHost.updateWidget(context, it) }
        WidgetUpdater.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?,
    ) {
        WidgetHost.updateWidget(context, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { context.widgeon.prefs.unbindWidget(it) }
        WidgetUpdater.refresh(context)
    }

    override fun onEnabled(context: Context) = WidgetUpdater.refresh(context)
    override fun onDisabled(context: Context) = WidgetUpdater.cancel(context)
}

class Widget1x1 : WidgeonWidgetProvider()
class Widget2x2 : WidgeonWidgetProvider()
class Widget4x2 : WidgeonWidgetProvider()
class Widget2x4 : WidgeonWidgetProvider()
class Widget4x4 : WidgeonWidgetProvider()
