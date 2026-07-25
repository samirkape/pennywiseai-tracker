package com.spendly.tracker.data.manager

import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun initialize() {
        val config = RequestConfiguration.Builder()
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR, "6FE0DB7793A6B70E9E4EDB62A027D691"))
            .build()
        MobileAds.setRequestConfiguration(config)
        MobileAds.initialize(context)
    }
}
