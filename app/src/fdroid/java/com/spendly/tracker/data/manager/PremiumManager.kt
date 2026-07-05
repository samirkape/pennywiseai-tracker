package com.spendly.tracker.data.manager

import android.app.Activity
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // F-Droid builds are always ad-free
    val isPremium: StateFlow<Boolean> = MutableStateFlow(true)

    fun connect() {}
    fun launchPurchaseFlow(activity: Activity) {}
    fun cleanup() {}
}
