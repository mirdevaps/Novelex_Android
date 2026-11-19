package com.novelex.dramas;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

/**
 * Manages interstitial ads displayed during natural user transitions
 * (exiting drama playback, changing menu sections, etc.).
 * Implements preloading and frequency capping to comply with Google AdMob policies.
 */
public final class CatalogInterstitial {

    private static final String TAG = "NovelexAds";
    private static final String AD_UNIT_ID = "ca-app-pub-3437419183521595/4855995915";
    private static final long MIN_INTERVAL_MS = 50_000L; // 50 seconds between interstitials

    private static CatalogInterstitial instance;

    private InterstitialAd ad;
    private boolean loading;
    private long lastShowTime = 0L;

    public static synchronized CatalogInterstitial getInstance() {
        if (instance == null) {
            instance = new CatalogInterstitial();
        }
        return instance;
    }

    public CatalogInterstitial() {}

    public CatalogInterstitial(Activity activity) {
        if (instance == null) {
            instance = this;
        }
        if (activity != null) {
            preload(activity.getApplicationContext());
        }
    }

    public void preload(Context context) {
        if (ad != null || loading || context == null) return;
        loading = true;
        InterstitialAd.load(context.getApplicationContext(), AD_UNIT_ID,
            new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd loadedAd) {
                    loading = false;
                    ad = loadedAd;
                    Log.d(TAG, "Navigation Interstitial preloaded successfully.");
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    loading = false;
                    ad = null;
                    Log.w(TAG, "Navigation Interstitial preload failed: " + error.getMessage());
                }
            });
    }

    /**
     * Displays the interstitial during a natural user transition (e.g. exit player, change tab).
     * Executes onComplete after the ad closes or immediately if not ready / in cooldown.
     */
    public void showOnNavigation(Activity activity, Runnable onComplete) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        long now = SystemClock.elapsedRealtime();
        boolean cooldownPassed = (now - lastShowTime) >= MIN_INTERVAL_MS;

        if (ad != null && cooldownPassed) {
            InterstitialAd showing = ad;
            ad = null;
            lastShowTime = now;

            showing.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Navigation Interstitial dismissed.");
                    preload(activity.getApplicationContext());
                    if (onComplete != null) onComplete.run();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                    Log.w(TAG, "Navigation Interstitial show failed: " + error.getMessage());
                    preload(activity.getApplicationContext());
                    if (onComplete != null) onComplete.run();
                }
            });

            showing.show(activity);
        } else {
            // Ad not ready or within cooldown period -> proceed smoothly without blocking
            preload(activity.getApplicationContext());
            if (onComplete != null) onComplete.run();
        }
    }

    public void resume() {
        // Kept for backward compatibility
    }

    public void pause() {
        // Kept for backward compatibility
    }
}
