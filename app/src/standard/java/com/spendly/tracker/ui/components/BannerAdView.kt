package com.spendly.tracker.ui.components

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

private const val MAX_RETRIES = 4
private const val BASE_RETRY_DELAY_MS = 15_000L

/**
 * Banner slot that only claims screen space once an ad has actually loaded, so a
 * no-fill or slow response leaves no empty strip above the bottom navigation.
 *
 * Retries with exponential backoff, and asks for a fresh ad whenever the screen
 * comes back to the foreground (a session that exhausted its retries is not stuck
 * without an ad until the next cold start).
 */
@Composable
fun BannerAdView(adUnitId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var adLoaded by remember { mutableStateOf(false) }

    val adView = remember(adUnitId) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            setAdUnitId(adUnitId)
        }
    }

    DisposableEffect(adView, lifecycleOwner) {
        var attempt = 0
        var firstResume = true
        val loadRunnable = Runnable { adView.loadAd(AdRequest.Builder().build()) }

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                attempt = 0
                adLoaded = true
                AdLog.d("Banner loaded ($adUnitId)")
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                adLoaded = false
                AdLog.w("Banner failed to load: code=${error.code} msg=${error.message}")
                if (attempt < MAX_RETRIES) {
                    val delay = BASE_RETRY_DELAY_MS shl attempt
                    attempt++
                    AdLog.d("Retrying in ${delay}ms (attempt $attempt of $MAX_RETRIES)")
                    handler.postDelayed(loadRunnable, delay)
                } else {
                    AdLog.w("Banner dropped after $MAX_RETRIES retries; slot hidden until next resume")
                }
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()

                Lifecycle.Event.ON_RESUME -> {
                    adView.resume()
                    // The first resume already has a load in flight; later resumes
                    // get one fresh request if the banner has nothing to show.
                    if (!firstResume && !adLoaded) {
                        attempt = 0
                        handler.removeCallbacks(loadRunnable)
                        loadRunnable.run()
                    }
                    firstResume = false
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        loadRunnable.run()

        onDispose {
            handler.removeCallbacks(loadRunnable)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            adView.adListener = object : AdListener() {}
            adView.destroy()
        }
    }

    AndroidView(
        factory = { adView },
        modifier = modifier
            .fillMaxWidth()
            .height(if (adLoaded) AdSize.BANNER.height.dp else 0.dp)
    )
}
