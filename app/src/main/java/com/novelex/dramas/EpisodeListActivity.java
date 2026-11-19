package com.novelex.dramas;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.nativead.NativeAd;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EpisodeListActivity extends AppCompatActivity {

    private static final String NATIVE_AD_UNIT = "ca-app-pub-3437419183521595/9373166289";

    private final Map<Integer, NativeAd> nativeAds = new HashMap<>();
    private final Map<Integer, NativeAd> pendingAds = new HashMap<>();
    private final Map<Integer, Long> impressionTimes = new HashMap<>();
    private final Set<Integer> impressedSlots = new HashSet<>();
    private final Set<Integer> loadingSlots = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean resumed;
    private EpisodeSheetAdapter adapter;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_episode_list);

        Drama drama = (Drama) getIntent().getSerializableExtra("drama");
        if (drama == null || drama.getEpisodes() == null) {
            finish();
            return;
        }

        int current = getIntent().getIntExtra("current", 0);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(R.id.episodeListRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).show(WindowInsetsCompat.Type.systemBars());

        findViewById(R.id.episodeListBack).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.episodeListTitle)).setText(drama.getTitle());
        ((TextView) findViewById(R.id.episodeListCount)).setText(drama.getEpisodes().size() + getString(R.string.ui_54) + (current + 1));

        RecyclerView list = findViewById(R.id.episodeList);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        list.setLayoutManager(manager);

        adapter = new EpisodeSheetAdapter(
            this,
            drama.getId(),
            drama.getEpisodes(),
            current,
            new EpisodeAccess(getSharedPreferences("novelex_prefs", MODE_PRIVATE), drama.getId()),
            nativeAds,
            this::loadNativeAd,
            (index, locked) -> {
                setResult(RESULT_OK, new Intent().putExtra("episode_index", index));
                finish();
            }
        );
        list.setAdapter(adapter);

        int targetPos = adapter.positionForEpisode(current);
        manager.scrollToPositionWithOffset(targetPos, 0);

        for (int slot = 0; slot < adapter.availableAdSlotCount(); slot++) {
            loadNativeAd(slot);
        }
    }

    private void loadNativeAd(int slot) {
        if (!resumed || isFinishing() || isDestroyed() || loadingSlots.contains(slot) || pendingAds.containsKey(slot)) return;
        // Reutiliza o anúncio caso ainda não tenha gerado impressão
        if (nativeAds.containsKey(slot) && !impressedSlots.contains(slot)) return;

        loadingSlots.add(slot);
        final NativeAd[] loaded = new NativeAd[1];
        new AdLoader.Builder(this, NATIVE_AD_UNIT)
            .forNativeAd(ad -> {
                loadingSlots.remove(slot);
                loaded[0] = ad;
                if (!resumed || isFinishing() || isDestroyed()) {
                    ad.destroy();
                    return;
                }
                pendingAds.put(slot, ad);
                long delay = Math.max(0L, impressionTimes.getOrDefault(slot, 0L) + 10_000L - SystemClock.elapsedRealtime());
                handler.postDelayed(() -> displayPendingAd(slot, ad), delay);
            })
            .withAdListener(new AdListener() {
                @Override
                public void onAdImpression() {
                    if (loaded[0] == null || loaded[0] != nativeAds.get(slot) || !impressedSlots.add(slot)) return;
                    impressionTimes.put(slot, SystemClock.elapsedRealtime());
                    // Quando gera impressão, solicita um novo anúncio para a próxima exibição
                    loadNativeAd(slot);
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    loadingSlots.remove(slot);
                    android.util.Log.w("NovelexAds", "EpisodeList native ad error slot " + slot + ": " + error.getMessage());
                    if (resumed) {
                        handler.postDelayed(() -> loadNativeAd(slot), 60_000L);
                    }
                }
            })
            .build()
            .loadAd(new AdRequest.Builder().build());
    }

    private void displayPendingAd(int slot, NativeAd ad) {
        if (!resumed || pendingAds.get(slot) != ad || isFinishing() || isDestroyed()) return;
        pendingAds.remove(slot);
        impressedSlots.remove(slot);
        impressionTimes.remove(slot);
        NativeAd old = nativeAds.put(slot, ad);
        if (adapter != null) {
            adapter.notifyNativeAdChanged(slot);
        }
        if (old != null && old != ad) {
            old.destroy();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        for (Map.Entry<Integer, NativeAd> entry : new HashMap<>(pendingAds).entrySet()) {
            int slot = entry.getKey();
            long delay = Math.max(0L, impressionTimes.getOrDefault(slot, 0L) + 10_000L - SystemClock.elapsedRealtime());
            handler.postDelayed(() -> displayPendingAd(slot, entry.getValue()), delay);
        }
        if (adapter != null) {
            for (int slot = 0; slot < adapter.availableAdSlotCount(); slot++) {
                loadNativeAd(slot);
            }
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        for (NativeAd ad : pendingAds.values()) if (ad != null) ad.destroy();
        for (NativeAd ad : nativeAds.values()) if (ad != null) ad.destroy();
        pendingAds.clear();
        nativeAds.clear();
        super.onDestroy();
    }
}
