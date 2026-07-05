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
    val isPremium: StateFlow<Boolean> = MutableStateFlow(true)
    val purchaseError: StateFlow<String?> = MutableStateFlow(null)
    val isPurchasing: StateFlow<Boolean> = MutableStateFlow(false)

    fun connect() {}
    fun launchPurchaseFlow(activity: Activity) {}
    fun clearError() {}
    fun cleanup() {}
}
