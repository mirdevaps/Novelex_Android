package com.novelex.dramas;

import android.content.Intent;
import android.os.*;
import android.text.*;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import androidx.recyclerview.widget.*;
import java.text.Normalizer;
import java.util.*;

public class GlobalSearchActivity extends AppCompatActivity {
    private static final String NATIVE_AD_UNIT = "ca-app-pub-3437419183521595/9373166289";
    private final List<Drama> results = new ArrayList<>();
    private final Map<Integer, com.google.android.gms.ads.nativead.NativeAd> nativeAds = new HashMap<>();
    private final Map<Integer, com.google.android.gms.ads.nativead.NativeAd> pendingAds = new HashMap<>();
    private final Map<Integer, Long> impressionTimes = new HashMap<>();
    private final Set<Integer> impressedSlots = new HashSet<>();
    private final Set<Integer> loadingSlots = new HashSet<>();
    private MainCombinedAdapter adapter;
    private EditText input;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int searchGeneration;
    private boolean resumed, adsReady;
    private List<Drama> allDramasCache;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_global_search);
        View root = findViewById(R.id.searchRoot);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> { androidx.core.graphics.Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()); v.setPadding(safe.left, safe.top, safe.right, safe.bottom); return insets; });
        findViewById(R.id.searchBack).setOnClickListener(v -> finish());
        input = findViewById(R.id.globalSearchInput);
        RecyclerView list = findViewById(R.id.globalSearchResults);
        int columns = getResources().getInteger(R.integer.catalog_columns);
        GridLayoutManager grid = new GridLayoutManager(this, columns);
        grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() { @Override public int getSpanSize(int position) { return adapter.isFullSpan(position) ? columns : 1; }});
        list.setLayoutManager(grid);
        adapter = new MainCombinedAdapter(this, results, Collections.emptyList(), this::openDrama, c -> {}, nativeAds, this::loadNativeAd);
        adapter.setDoramaCatalog(true);
        adapter.setSectionContent(getString(R.string.search_all), getString(R.string.search_start));
        list.setAdapter(adapter);
        if (BuildConfig.DEBUG) com.google.android.gms.ads.MobileAds.setRequestConfiguration(new com.google.android.gms.ads.RequestConfiguration.Builder().setTestDeviceIds(Collections.singletonList("C25AC9B220BD3A44921ABFD2AEACBED7")).build());
        com.google.android.gms.ads.MobileAds.initialize(this, status -> { adsReady = true; if (resumed && !results.isEmpty()) adapter.notifyDataSetChanged(); });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ scheduleSearch(s.toString()); } public void afterTextChanged(Editable e){}
        });
        input.requestFocus(); input.postDelayed(() -> ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT), 180);
    }

    private void scheduleSearch(String query) {
        int generation = ++searchGeneration;
        handler.postDelayed(() -> { if (generation == searchGeneration) search(query, generation); }, 180);
    }
    private void search(String raw, int generation) {
        String query = normalize(raw);
        if (query.isEmpty()) { results.clear(); adapter.setSectionContent(getString(R.string.search_all), getString(R.string.search_start)); adapter.notifyDataSetChanged(); return; }
        new Thread(() -> {
            if (allDramasCache == null) {
                allDramasCache = DatabaseHelper.getInstance(this).getAllDramas(-1, 0);
            }
            List<Scored> scored = new ArrayList<>();
            String[] terms = query.split("\\s+");
            for (Drama drama : allDramasCache) {
                String title = normalize(drama.getTitle());
                String genre = normalize(drama.getGenre());
                String synopsis = normalize(drama.getSynopsis());
                String audio = normalize(drama.getAudio());
                boolean dorama = drama.getId().startsWith("dorama_") || genre.contains("dorama");
                String type = dorama ? "dorama drama asiatico coreano korea" : "novela curta novelinha reel vertical";
                int score = title.equals(query) ? 1000 : title.startsWith(query) ? 650 : title.contains(query) ? 420 : 0;
                boolean all = true;
                for (String term : terms) {
                    int termScore = title.startsWith(term) ? 120 : title.contains(term) ? 90 : genre.contains(term) ? 55 : type.contains(term) ? 48 : audio.contains(term) ? 38 : synopsis.contains(term) ? 22 : 0;
                    if (termScore == 0) all = false; score += termScore;
                }
                if (all && score > 0) scored.add(new Scored(drama, score));
            }
            scored.sort((a,b) -> b.score != a.score ? Integer.compare(b.score,a.score) : a.drama.getTitle().compareToIgnoreCase(b.drama.getTitle()));
            List<Drama> found = new ArrayList<>(); for (int i=0;i<Math.min(120,scored.size());i++) found.add(scored.get(i).drama);
            runOnUiThread(() -> { if (generation != searchGeneration || isFinishing()) return; results.clear(); results.addAll(found); adapter.setSectionContent(getString(R.string.search_all), found.isEmpty() ? getString(R.string.search_no_results) : getString(R.string.search_results)); adapter.notifyDataSetChanged(); });
        }, "global-catalog-search").start();
    }
    private static String normalize(String value) { if (value == null) return ""; return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim(); }
    private void loadNativeAd(int slot) {
        if (!resumed || !adsReady || isFinishing() || isDestroyed() || loadingSlots.contains(slot) || pendingAds.containsKey(slot)) return;
        if (nativeAds.containsKey(slot) && !impressedSlots.contains(slot)) return;
        loadingSlots.add(slot);
        final com.google.android.gms.ads.nativead.NativeAd[] loaded = new com.google.android.gms.ads.nativead.NativeAd[1];
        new com.google.android.gms.ads.AdLoader.Builder(this, NATIVE_AD_UNIT).forNativeAd(ad -> {
            loadingSlots.remove(slot); loaded[0] = ad;
            if (!resumed || isFinishing() || isDestroyed()) { ad.destroy(); return; }
            pendingAds.put(slot, ad);
            long delay = Math.max(0L, impressionTimes.getOrDefault(slot, 0L) + 10_000L - android.os.SystemClock.elapsedRealtime());
            handler.postDelayed(() -> displayPendingAd(slot, ad), delay);
        }).withAdListener(new com.google.android.gms.ads.AdListener() {
            @Override public void onAdImpression() {
                if (loaded[0] == null || loaded[0] != nativeAds.get(slot) || !impressedSlots.add(slot)) return;
                impressionTimes.put(slot, android.os.SystemClock.elapsedRealtime()); loadNativeAd(slot);
            }
            @Override public void onAdFailedToLoad(@androidx.annotation.NonNull com.google.android.gms.ads.LoadAdError error) {
                loadingSlots.remove(slot); android.util.Log.w("NovelexAds", "Search native: " + error);
                if (resumed) handler.postDelayed(() -> loadNativeAd(slot), 60_000L);
            }
        }).build().loadAd(new com.google.android.gms.ads.AdRequest.Builder().build());
    }
    private void displayPendingAd(int slot, com.google.android.gms.ads.nativead.NativeAd ad) {
        if (!resumed || pendingAds.get(slot) != ad || isFinishing() || isDestroyed()) return;
        pendingAds.remove(slot); impressedSlots.remove(slot); impressionTimes.remove(slot);
        com.google.android.gms.ads.nativead.NativeAd old = nativeAds.put(slot, ad);
        adapter.notifyNativeAdChanged(slot); if (old != null && old != ad) old.destroy();
    }
    private void openDrama(Drama drama) { new Thread(() -> { drama.setEpisodes(DatabaseHelper.getInstance(this).getEpisodesForDrama(drama.getId())); runOnUiThread(() -> { if (!isFinishing()) startActivity(new Intent(this, DramaPlayerActivity.class).putExtra("drama", drama).putExtra("start_episode_index", getSharedPreferences("novelex_prefs", MODE_PRIVATE).getInt("last_episode_index_" + drama.getId(), 0))); }); }).start(); }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        for (Map.Entry<Integer, com.google.android.gms.ads.nativead.NativeAd> entry : new HashMap<>(pendingAds).entrySet()) {
            int slot = entry.getKey(); long delay = Math.max(0L, impressionTimes.getOrDefault(slot, 0L) + 10_000L - android.os.SystemClock.elapsedRealtime());
            handler.postDelayed(() -> displayPendingAd(slot, entry.getValue()), delay);
        }
        if (!results.isEmpty()) adapter.notifyDataSetChanged();
    }
    @Override protected void onPause() { resumed = false; super.onPause(); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        for (com.google.android.gms.ads.nativead.NativeAd ad : pendingAds.values()) ad.destroy();
        for (com.google.android.gms.ads.nativead.NativeAd ad : nativeAds.values()) ad.destroy();
        pendingAds.clear(); nativeAds.clear(); super.onDestroy();
    }
    private static final class Scored { final Drama drama; final int score; Scored(Drama d,int s){drama=d;score=s;} }
}
