package com.spendly.tracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class SavingsWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = SavingsWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Refresh per-widget data on launcher updates and after app upgrades.
        SavingsWidgetUpdateWorker.enqueueOneShot(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        SavingsWidgetUpdateWorker.enqueuePeriodicUpdate(context)
        SavingsWidgetUpdateWorker.enqueueOneShot(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        SavingsWidgetUpdateWorker.cancelPeriodicUpdate(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { SavingsWidgetConfigStore.clear(context, it) }
    }
}
