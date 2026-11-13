package com.spendly.tracker.widget

import android.content.Context

/**
 * Remembers which savings goal each home-screen widget tracks.
 *
 * A missing entry means "follow the active goal", which keeps widgets added before goal
 * selection existed behaving the same way they used to.
 */
object SavingsWidgetConfigStore {

    private const val PREFS_NAME = "savings_widget_config"
    private const val GOAL_KEY_PREFIX = "goal_"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(appWidgetId: Int) = "$GOAL_KEY_PREFIX$appWidgetId"

    /** The goal explicitly chosen for [appWidgetId], or null to follow the active goal. */
    fun goalId(context: Context, appWidgetId: Int): String? =
        preferences(context).getString(key(appWidgetId), null)

    fun setGoalId(context: Context, appWidgetId: Int, goalId: String) {
        preferences(context).edit().putString(key(appWidgetId), goalId).apply()
    }

    fun clear(context: Context, appWidgetId: Int) {
        preferences(context).edit().remove(key(appWidgetId)).apply()
    }
}
