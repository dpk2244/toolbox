package com.pushpendratech.toolbox.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.pushpendratech.toolbox.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob integration — TEST ad units by default (see gradle.properties).
 * Rules honoured: banner only at the bottom of Home/Category screens (never over file pickers,
 * progress, or results); interstitial at most once per 3 minutes and only after a *completed*
 * tool run when the user starts another action — never after every tap. App works fully if ads fail/offline.
 */
object Ads {
    private val initialised = AtomicBoolean(false)
    @Volatile private var lastInterstitialMs = 0L
    fun init(context: Context) { if (initialised.compareAndSet(false, true)) MobileAds.initialize(context.applicationContext) {} }

    fun showInterstitialIfDue(activity: Activity) {
        val now = System.currentTimeMillis()
        if (now - lastInterstitialMs < 180_000) return
        InterstitialAd.load(activity, BuildConfig.ADMOB_INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    lastInterstitialMs = System.currentTimeMillis()
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {}
                    ad.show(activity)
                }
                override fun onAdFailedToLoad(error: LoadAdError) { /* silent — ads must never block tools */ }
            })
    }
}

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adView = remember { AdView(context).apply { setAdSize(AdSize.BANNER); adUnitId = BuildConfig.ADMOB_BANNER_ID } }
    DisposableEffect(adView) { adView.loadAd(AdRequest.Builder().build()); onDispose { adView.destroy() } }
    AndroidView(factory = { adView }, modifier = modifier.fillMaxWidth())
}
