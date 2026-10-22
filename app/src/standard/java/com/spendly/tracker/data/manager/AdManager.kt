package com.spendly.tracker.data.manager

import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.spendly.tracker.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Developer device, registered as a test device. Value is the hash AdMob logs on
// first request (uppercase hex, no dashes) and is not a personal identifier.
private const val DEV_TEST_DEVICE_ID = "6FE0DB7793A6B70E9E4EDB62A027D691"

@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun initialize() {
        val configuration = RequestConfiguration.Builder()
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .setTestDeviceIds(testDeviceIds())
            .build()
        MobileAds.setRequestConfiguration(configuration)
        MobileAds.initialize(context)
    }

    /**
     * The developer device stays a test device in every build: it is used to
     * exercise the ad flow on release APKs, and serving real ads there would risk
     * invalid-traffic violations from accidental interactions. Emulators only
     * appear in debug builds.
     */
    private fun testDeviceIds(): List<String> =
        listOfNotNull(
            DEV_TEST_DEVICE_ID,
            AdRequest.DEVICE_ID_EMULATOR.takeIf { BuildConfig.DEBUG },
        )
}
