package com.novelex.dramas;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import androidx.recyclerview.widget.*;
import com.google.firebase.firestore.*;
import java.util.*;

public class HomeActivity extends AppCompatActivity {
    private static final String NATIVE_AD_UNIT = "ca-app-pub-3437419183521595/9373166289";
    private final List<Drama> titles = new ArrayList<>();
    private final List<Drama> highlights = new ArrayList<>();
    private final List<Drama> releases = new ArrayList<>();
    private MainCombinedAdapter adapter;
    private MainCombinedAdapter releasesAdapter;
    private HomeHighlightAdapter highlightAdapter;
    private androidx.viewpager2.widget.ViewPager2 highlightPager;
    private android.widget.LinearLayout dots;
    private final android.os.Handler sliderHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Map<Integer, com.google.android.gms.ads.nativead.NativeAd> nativeAds = new HashMap<>();
    private final Map<Integer, com.google.android.gms.ads.nativead.NativeAd> pendingAds = new HashMap<>();
    private final Map<Integer, Long> impressionTimes = new HashMap<>();
    private final Set<Integer> impressedSlots = new HashSet<>();
    private final Set<Integer> loadingSlots = new HashSet<>();
    private boolean resumed, adsReady;
    private final Runnable advanceSlide = new Runnable() { @Override public void run() {
        if (!highlights.isEmpty()) highlightPager.setCurrentItem((highlightPager.getCurrentItem() + 1) % highlights.size(), true);
        sliderHandler.postDelayed(this, 5000);
    }};
    private com.google.firebase.firestore.ListenerRegistration ranking;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_home);
        View root = findViewById(R.id.homeRoot);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom); return insets;
        });
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).show(WindowInsetsCompat.Type.systemBars());
        findViewById(R.id.homeNavShorts).setOnClickListener(v -> openCatalog(false));
        findViewById(R.id.homeNavDoramas).setOnClickListener(v -> openCatalog(true));
        findViewById(R.id.homeNavSaved).setOnClickListener(v -> openCatalogTab(1));
        findViewById(R.id.homeNavContinue).setOnClickListener(v -> openCatalogTab(2));
        findViewById(R.id.homeSearch).setOnClickListener(v -> startActivity(new Intent(this, GlobalSearchActivity.class)));
        highlightPager = findViewById(R.id.homeHighlights);
        dots = findViewById(R.id.homeDots);
        highlightAdapter = new HomeHighlightAdapter(highlights, this::openDrama);
        highlightPager.setAdapter(highlightAdapter);
        highlightPager.setOffscreenPageLimit(2);
        highlightPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { updateDots(position); sliderHandler.removeCallbacks(advanceSlide); sliderHandler.postDelayed(advanceSlide, 5000); }
        });
        RecyclerView list = findViewById(R.id.homeRanking);
        RecyclerView releasesList = findViewById(R.id.homeReleases);
        int columns = getResources().getInteger(R.integer.catalog_columns);
        GridLayoutManager manager = new GridLayoutManager(this, columns);
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return adapter.isFullSpan(position) ? columns : 1; }
        });
        list.setLayoutManager(manager);
        adapter = new MainCombinedAdapter(this, titles, Collections.emptyList(), this::openDrama, category -> {}, new HashMap<>(), null);
        adapter.setDoramaCatalog(true);
        adapter.setSectionContent(getString(R.string.home_popular), getString(R.string.home_loading));
        list.setAdapter(adapter);
        GridLayoutManager releasesGrid = new GridLayoutManager(this, columns);
        releasesList.setLayoutManager(releasesGrid);
        releasesAdapter = new MainCombinedAdapter(this, releases, Collections.emptyList(), this::openDrama, category -> {}, new HashMap<>(), null);
        releasesAdapter.setDoramaCatalog(true);
        releasesAdapter.setSectionContent(getString(R.string.home_releases), getString(R.string.home_releases_info));
        releasesGrid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() { @Override public int getSpanSize(int position) { return releasesAdapter.isFullSpan(position) ? columns : 1; }});
        releasesList.setAdapter(releasesAdapter);
        if (BuildConfig.DEBUG) com.google.android.gms.ads.MobileAds.setRequestConfiguration(new com.google.android.gms.ads.RequestConfiguration.Builder().setTestDeviceIds(Collections.singletonList("C25AC9B220BD3A44921ABFD2AEACBED7")).build());
        com.google.android.gms.ads.MobileAds.initialize(this, status -> { adsReady = true; if (resumed) loadNativeAd(0); });
        CatalogInterstitial.getInstance().preload(this);
    }
    private void openCatalog(boolean doramas) {
        CatalogInterstitial.getInstance().showOnNavigation(this, () -> {
            startActivity(new Intent(this, MainActivity.class).putExtra("open_doramas", doramas));
        });
    }
    private void openCatalogTab(int tab) {
        CatalogInterstitial.getInstance().showOnNavigation(this, () -> {
            startActivity(new Intent(this, MainActivity.class).putExtra("open_bottom_tab", tab));
        });
    }
    private void openCategory(String category) {
        CatalogInterstitial.getInstance().showOnNavigation(this, () -> {
            startActivity(new Intent(this, MainActivity.class).putExtra("open_category", category));
        });
    }
    private void loadNativeAd(int slot) {
        if (!resumed || !adsReady || isFinishing() || isDestroyed() || loadingSlots.contains(slot) || pendingAds.containsKey(slot)) return;
        if (nativeAds.containsKey(slot) && !impressedSlots.contains(slot)) return;
        loadingSlots.add(slot);
        final com.google.android.gms.ads.nativead.NativeAd[] loaded = new com.google.android.gms.ads.nativead.NativeAd[1];
        new com.google.android.gms.ads.AdLoader.Builder(this, NATIVE_AD_UNIT).forNativeAd(ad -> {
            loadingSlots.remove(slot); loaded[0] = ad;
            if (!resumed || isFinishing() || isDestroyed()) { ad.destroy(); return; }
            pendingAds.put(slot, ad);
            long delay = Math.max(0, impressionTimes.getOrDefault(slot, 0L) + 10_000L - android.os.SystemClock.elapsedRealtime());
            sliderHandler.postDelayed(() -> displayPendingAd(slot, ad), delay);
        }).withAdListener(new com.google.android.gms.ads.AdListener() {
            @Override public void onAdImpression() {
                if (loaded[0] == null || loaded[0] != nativeAds.get(slot) || !impressedSlots.add(slot)) return;
                impressionTimes.put(slot, android.os.SystemClock.elapsedRealtime());
                loadNativeAd(slot);
            }
            @Override public void onAdFailedToLoad(@androidx.annotation.NonNull com.google.android.gms.ads.LoadAdError error) {
                loadingSlots.remove(slot); android.util.Log.w("NovelexAds", "Home native: " + error);
                if (resumed) sliderHandler.postDelayed(() -> loadNativeAd(slot), 60_000L);
            }
        }).build().loadAd(new com.google.android.gms.ads.AdRequest.Builder().build());
    }
    private void displayPendingAd(int slot, com.google.android.gms.ads.nativead.NativeAd ad) {
        if (!resumed || pendingAds.get(slot) != ad || isFinishing() || isDestroyed()) return;
        pendingAds.remove(slot); impressedSlots.remove(slot); impressionTimes.remove(slot);
        com.google.android.gms.ads.nativead.NativeAd old = nativeAds.put(slot, ad);
        bindHomeNativeAd(ad); if (old != null && old != ad) old.destroy();
    }
    private void bindHomeNativeAd(com.google.android.gms.ads.nativead.NativeAd ad) {
        com.google.android.gms.ads.nativead.NativeAdView view = findViewById(R.id.homeNativeAd);
        android.widget.TextView headline = view.findViewById(R.id.nativeAdHeadline);
        android.widget.TextView body = view.findViewById(R.id.nativeAdBody);
        android.widget.TextView action = view.findViewById(R.id.nativeAdCallToAction);
        android.widget.ImageView icon = view.findViewById(R.id.nativeAdIcon);
        view.setHeadlineView(headline); view.setBodyView(body); view.setCallToActionView(action); view.setIconView(icon);
        view.setMediaView(view.findViewById(R.id.nativeAdMedia)); headline.setText(ad.getHeadline());
        body.setText(ad.getBody() == null ? "" : ad.getBody()); body.setVisibility(ad.getBody() == null ? View.GONE : View.VISIBLE);
        action.setText(ad.getCallToAction() == null ? "" : ad.getCallToAction()); action.setVisibility(ad.getCallToAction() == null ? View.GONE : View.VISIBLE);
        if (ad.getIcon() != null) { icon.setImageDrawable(ad.getIcon().getDrawable()); icon.setVisibility(View.VISIBLE); } else icon.setVisibility(View.GONE);
        view.setVisibility(View.VISIBLE); view.setNativeAd(ad);
    }
    private void openDrama(Drama drama) {
        new Thread(() -> {
            drama.setEpisodes(DatabaseHelper.getInstance(this).getEpisodesForDrama(drama.getId()));
            runOnUiThread(() -> { if (!isFinishing()) startActivity(new Intent(this, DramaPlayerActivity.class).putExtra("drama", drama)
                .putExtra("start_episode_index", getSharedPreferences("novelex_prefs", MODE_PRIVATE).getInt("last_episode_index_" + drama.getId(), 0))); });
        }).start();
    }
    private void updateDots(int selected) {
        dots.removeAllViews();
        for (int i=0; i<highlights.size(); i++) {
            View dot = new View(this);
            android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(dp(i == selected ? 18 : 6), dp(6));
            p.setMargins(dp(3),0,dp(3),0); dot.setLayoutParams(p);
            dot.setBackgroundColor(getColor(i == selected ? R.color.novelex_primary_pink : R.color.novelex_text_muted));
            dots.addView(dot);
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onStart() {
        super.onStart();
        sliderHandler.postDelayed(advanceSlide, 5000);
        ranking = FirebaseFirestore.getInstance().collection("dramas")
            .orderBy("likes_count", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    adapter.setSectionContent(getString(R.string.home_popular), getString(R.string.home_error)); return;
                }
                if (snapshot == null) return;
                List<String> ids = new ArrayList<>();
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    Number likes = (Number) doc.get("likes_count");
                    if (likes != null && likes.longValue() > 0) ids.add(doc.getId());
                }
                new Thread(() -> {
                    Map<String, Drama> catalog = new HashMap<>();
                    List<Drama> fullCatalog = DatabaseHelper.getInstance(this).getAllDramas(-1, 0);
                    for (Drama d : fullCatalog) catalog.put(d.getId(), d);
                    List<Drama> ordered = new ArrayList<>();
                    for (String id : ids) if (catalog.containsKey(id)) ordered.add(catalog.get(id));
                    List<Drama> featured = new ArrayList<>(ordered.subList(0, Math.min(8, ordered.size())));
                    Set<String> featuredIds = new HashSet<>();
                    for (Drama d : featured) featuredIds.add(d.getId());
                    fullCatalog.sort((a, b) -> {
                        int year = Integer.compare(b.getYear(), a.getYear());
                        if (year != 0) return year;
                        int episodes = Integer.compare(b.getEpisodesCount(), a.getEpisodesCount());
                        return episodes != 0 ? episodes : a.getTitle().compareToIgnoreCase(b.getTitle());
                    });
                    List<Drama> latest = new ArrayList<>();
                    for (Drama d : fullCatalog) {
                        if (!d.getPoster().isEmpty()) latest.add(d);
                        if (latest.size() == 16) break;
                    }
                    boolean wantDorama = featured.stream().noneMatch(d -> d.getId().startsWith("dorama_"));
                    while (featured.size() < 8) {
                        Drama choice = null;
                        for (Drama d : fullCatalog) {
                            boolean dorama = d.getId().startsWith("dorama_") || d.getGenre().toLowerCase(java.util.Locale.ROOT).contains("dorama");
                            if (!featuredIds.contains(d.getId()) && dorama == wantDorama && !d.getBanner().isEmpty()) { choice = d; break; }
                        }
                        if (choice == null) for (Drama d : fullCatalog) if (!featuredIds.contains(d.getId()) && !d.getBanner().isEmpty()) { choice = d; break; }
                        if (choice == null) break;
                        featured.add(choice); featuredIds.add(choice.getId()); wantDorama = !wantDorama;
                    }
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return;
                        titles.clear(); titles.addAll(ordered);
                        highlights.clear(); highlights.addAll(featured);
                        releases.clear(); releases.addAll(latest);
                        highlightAdapter.notifyDataSetChanged();
                        highlightPager.setVisibility(highlights.isEmpty() ? View.GONE : View.VISIBLE);
                        updateDots(0);
                        adapter.setSectionContent(getString(R.string.home_popular), getString(ordered.isEmpty() ? R.string.home_empty : R.string.home_ranking_info));
                        adapter.notifyDataSetChanged();
                        releasesAdapter.notifyDataSetChanged();
                    });
                }).start();
            });
    }
    @Override protected void onStop() {
        resumed = false;
        if (ranking != null) ranking.remove();
        sliderHandler.removeCallbacks(advanceSlide);
        super.onStop();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        loadNativeAd(0);
        for (Map.Entry<Integer, com.google.android.gms.ads.nativead.NativeAd> entry : new HashMap<>(pendingAds).entrySet()) {
            int slot = entry.getKey();
            long delay = Math.max(0L, impressionTimes.getOrDefault(slot, 0L) + 10_000L - android.os.SystemClock.elapsedRealtime());
            sliderHandler.postDelayed(() -> displayPendingAd(slot, entry.getValue()), delay);
        }
    }
    @Override protected void onDestroy() {
        sliderHandler.removeCallbacksAndMessages(null);
        for (com.google.android.gms.ads.nativead.NativeAd ad : pendingAds.values()) ad.destroy();
        for (com.google.android.gms.ads.nativead.NativeAd ad : nativeAds.values()) ad.destroy();
        pendingAds.clear(); nativeAds.clear(); super.onDestroy();
    }
}
