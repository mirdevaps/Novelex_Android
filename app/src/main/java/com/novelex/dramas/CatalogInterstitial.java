package com.novelex.dramas;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import com.google.android.gms.ads.*;
import com.google.android.gms.ads.interstitial.*;

/** Counts foreground catalog time and requests shortly before the display window. */
final class CatalogInterstitial {
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long elapsed, started;
    private boolean active, loading;
    private InterstitialAd ad;
    private final Runnable tick = this::tick;

    CatalogInterstitial(Activity activity) { this.activity = activity; }
    void resume() {
        if (active) return;
        active = true; started = SystemClock.elapsedRealtime(); handler.post(tick);
    }
    void pause() {
        if (active) elapsed += SystemClock.elapsedRealtime() - started;
        active = false; handler.removeCallbacks(tick);
    }
    private void tick() {
        if (!active || activity.isFinishing() || activity.isDestroyed()) return;
        long age = elapsed + SystemClock.elapsedRealtime() - started;
        if (age >= 110_000 && ad == null && !loading) {
            loading = true;
            InterstitialAd.load(activity, "ca-app-pub-3437419183521595/4855995915",
                new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull InterstitialAd loaded) {
                        loading = false; ad = loaded;
                    }
                    @Override public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    android.util.Log.w("NovelexAds", "CatalogInterstitial.java load: " + error);
                        loading = false; reset();
                    }
                });
        }
        if (age >= 120_000 && ad != null) {
            InterstitialAd showing = ad; ad = null; reset();
            showing.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override public void onAdDismissedFullScreenContent() { reset(); }
                @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError error) { android.util.Log.w("NovelexAds", "Interstitial show: " + error); reset(); }
            });
            showing.show(activity);
        }
        if (active) handler.postDelayed(tick, 1000);
    }
    private void reset() { elapsed = 0; started = SystemClock.elapsedRealtime(); }
}
