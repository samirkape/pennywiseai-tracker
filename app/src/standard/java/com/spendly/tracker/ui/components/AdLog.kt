package com.spendly.tracker.ui.components

import android.util.Log

/**
 * Ad diagnostics logger.
 *
 * Release builds strip `android.util.Log.*` calls via `proguard-rules.pro`
 * (`-assumenosideeffects`), which previously made ad load failures invisible on
 * release installs. `Log.println` is not matched by that rule, so ad failures
 * stay observable in logcat for release APKs without loosening the global rule.
 */
internal object AdLog {

    private const val TAG = "Ad"

    fun d(message: String) = Log.println(Log.DEBUG, TAG, message)

    fun w(message: String) = Log.println(Log.WARN, TAG, message)
}
