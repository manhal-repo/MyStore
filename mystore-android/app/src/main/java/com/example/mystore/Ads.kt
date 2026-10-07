package com.example.mystore

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object Ads {
    fun init(ctx: Context) { MobileAds.initialize(ctx) {} }

    /** إعلان فتح التطبيق (App Open): عند تشغيل التطبيق فقط. true = ظهر فعليًا ثم أُغلق. */
    fun showAppOpen(activity: Activity, onResult: (Boolean) -> Unit) {
        AppOpenAd.load(activity, Config.APP_OPEN_ID, AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    var shown = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() { shown = true }
                        override fun onAdDismissedFullScreenContent() { onResult(shown) }
                        override fun onAdFailedToShowFullScreenContent(e: AdError) { onResult(false) }
                    }
                    ad.show(activity)
                }
                override fun onAdFailedToLoad(e: LoadAdError) { onResult(false) }
            })
    }

    /**
     * إعلان بيني عند التنزيل فقط. يحمّل الإعلان ثم يعرضه. onResult(true) فقط إذا ظهر الإعلان فعليًا ثم أُغلق.
     * أي فشل (تحميل/عرض) ← false ولا يجب تنفيذ المهمة.
     */
    fun show(activity: Activity, onResult: (Boolean) -> Unit) {
        InterstitialAd.load(activity, Config.INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    var shown = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() { shown = true }
                        override fun onAdDismissedFullScreenContent() { onResult(shown) }
                        override fun onAdFailedToShowFullScreenContent(e: AdError) { onResult(false) }
                    }
                    ad.show(activity)
                }
                override fun onAdFailedToLoad(e: LoadAdError) { onResult(false) }
            })
    }
}
