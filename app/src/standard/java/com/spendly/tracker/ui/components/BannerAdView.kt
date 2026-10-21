package com.spendly.tracker.ui.components

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

private const val TAG = "BannerAd"
private const val MAX_RETRIES = 4
private const val BASE_RETRY_DELAY_MS = 15_000L

@Composable
fun BannerAdView(adUnitId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    val adView = remember(adUnitId) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            setAdUnitId(adUnitId)
        }
    }

    DisposableEffect(adView) {
        var attempt = 0
        val loadRunnable = Runnable { adView.loadAd(AdRequest.Builder().build()) }

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                attempt = 0
                Log.d(TAG, "Ad loaded")
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "Ad failed to load: code=${error.code} msg=${error.message}")
                if (attempt < MAX_RETRIES) {
                    val delay = BASE_RETRY_DELAY_MS shl attempt
                    attempt++
                    handler.postDelayed(loadRunnable, delay)
                }
            }
        }
        loadRunnable.run()

        onDispose {
            handler.removeCallbacks(loadRunnable)
            adView.adListener = object : AdListener() {}
            adView.destroy()
        }
    }

    AndroidView(
        factory = { adView },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    )
}
