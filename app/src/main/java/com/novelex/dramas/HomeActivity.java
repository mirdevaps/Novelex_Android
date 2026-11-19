package com.novelex.dramas;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import androidx.recyclerview.widget.*;
import com.google.firebase.database.*;
import java.util.*;

public class HomeActivity extends AppCompatActivity {
    private static final String NATIVE_AD_UNIT = "ca-app-pub-3437419183521595/9373166289";
    private final List<Drama> titles = new ArrayList<>();
    private final List<Drama> highlights = new ArrayList<>();
    private final List<Drama> releases = new ArrayList<>();
    private final List<Drama> continueItems = new ArrayList<>();
    private HomeContinueAdapter continueAdapter;
    private View continueSection;
    private RecyclerView continueList;
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
    private Query rankingQuery;
    private ValueEventListener rankingListener;
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
        continueSection = findViewById(R.id.homeContinueSection);
        continueList = findViewById(R.id.homeContinueList);
        continueList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        continueAdapter = new HomeContinueAdapter(this, continueItems, getSharedPreferences("novelex_prefs", MODE_PRIVATE), this::openDrama);
        continueList.setAdapter(continueAdapter);
        View continueSeeAll = findViewById(R.id.homeContinueSeeAll);
        if (continueSeeAll != null) continueSeeAll.setOnClickListener(v -> openCatalogTab(2));
        RecyclerView list = findViewById(R.id.homeRanking);
        RecyclerView releasesList = findViewById(R.id.homeReleases);
        int columns = getResources().getInteger(R.integer.catalog_columns);
        GridLayoutManager manager = new GridLayoutManager(this, columns);
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return adapter.isFullSpan(position) ? columns : 1; }
        });
        list.setLayoutManager(manager);
        adapter = new MainCombinedAdapter(this, titles, Collections.emptyList(), this::openDrama, category -> {}, nativeAds, this::loadNativeAd);
        adapter.setDoramaCatalog(true);
        adapter.setAdSpacing(4, 6, 3);
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
        com.google.android.gms.ads.MobileAds.initialize(this, status -> {
            adsReady = true;
            for (int s = 0; s < 3; s++) loadNativeAd(s);
        });
        CatalogInterstitial.getInstance().preload(this);
        for (int s = 0; s < 3; s++) loadNativeAd(s);
        loadLocalCatalogInstant();
        loadRankingFromFirebase();
    }

    private void loadLocalCatalogInstant() {
        new Thread(() -> {
            List<Drama> fullCatalog = DatabaseHelper.getInstance(this).getAllDramas(-1, 0);
            if (fullCatalog.isEmpty()) return;

            List<Drama> featured = new ArrayList<>();
            Set<String> featuredIds = new HashSet<>();
            for (Drama d : fullCatalog) {
                if (d.getBanner() != null && !d.getBanner().isEmpty()) {
                    featured.add(d);
                    featuredIds.add(d.getId());
                    if (featured.size() == 8) break;
                }
            }

            // Lançamentos ordenados por ano mais recente
            List<Drama> sortedReleases = new ArrayList<>(fullCatalog);
            sortedReleases.sort((a, b) -> {
                int year = Integer.compare(b.getYear(), a.getYear());
                if (year != 0) return year;
                int episodes = Integer.compare(b.getEpisodesCount(), a.getEpisodesCount());
                return episodes != 0 ? episodes : a.getTitle().compareToIgnoreCase(b.getTitle());
            });

            List<Drama> latest = new ArrayList<>();
            for (Drama d : sortedReleases) {
                if (d.getPoster() != null && !d.getPoster().isEmpty()) latest.add(d);
                if (latest.size() == 16) break;
            }

            // Popular preliminar local (ordenado por maior número de episódios/volume)
            List<Drama> sortedPopular = new ArrayList<>(fullCatalog);
            sortedPopular.sort((a, b) -> {
                int episodes = Integer.compare(b.getEpisodesCount(), a.getEpisodesCount());
                return episodes != 0 ? episodes : a.getTitle().compareToIgnoreCase(b.getTitle());
            });

            List<Drama> initialPopular = new ArrayList<>();
            for (Drama d : sortedPopular) {
                if (d.getPoster() != null && !d.getPoster().isEmpty()) initialPopular.add(d);
                if (initialPopular.size() == 28) break;
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (highlights.isEmpty() && !featured.isEmpty()) {
                    highlights.addAll(featured);
                    highlightAdapter.notifyDataSetChanged();
                    highlightPager.setVisibility(View.VISIBLE);
                    updateDots(0);
                }
                if (releases.isEmpty() && !latest.isEmpty()) {
                    releases.addAll(latest);
                    releasesAdapter.notifyDataSetChanged();
                }
                if (titles.isEmpty() && !initialPopular.isEmpty()) {
                    titles.addAll(initialPopular);
                    adapter.setSectionContent(getString(R.string.home_popular), getString(R.string.home_ranking_info));
                    adapter.notifyDataSetChanged();
                }
            });
            loadContinueWatching();
        }, "home-instant-local").start();
    }

    private void loadContinueWatching() {
        new Thread(() -> {
            android.content.SharedPreferences prefs = getSharedPreferences("novelex_prefs", MODE_PRIVATE);
            List<Drama> all = DatabaseHelper.getInstance(this).getAllDramas(-1, 0);
            List<Drama> watched = new ArrayList<>();
            for (Drama d : all) {
                if (prefs.getLong("last_watched_" + d.getId(), 0L) > 0L) {
                    watched.add(d);
                }
            }
            watched.sort((a, b) -> Long.compare(
                prefs.getLong("last_watched_" + b.getId(), 0L),
                prefs.getLong("last_watched_" + a.getId(), 0L)
            ));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                continueItems.clear();
                continueItems.addAll(watched);
                continueAdapter.notifyDataSetChanged();
                continueSection.setVisibility(continueItems.isEmpty() ? View.GONE : View.VISIBLE);
            });
        }, "home-continue-load").start();
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
            long delay = Math.max(0L, impressionTimes.getOrDefault(slot, 0L) + 10_000L - android.os.SystemClock.elapsedRealtime());
            sliderHandler.postDelayed(() -> displayPendingAd(slot, ad), delay);
        }).withAdListener(new com.google.android.gms.ads.AdListener() {
            @Override public void onAdImpression() {
                if (loaded[0] == null || loaded[0] != nativeAds.get(slot) || !impressedSlots.add(slot)) return;
                impressionTimes.put(slot, android.os.SystemClock.elapsedRealtime());
                loadNativeAd(slot);
            }
            @Override public void onAdFailedToLoad(@androidx.annotation.NonNull com.google.android.gms.ads.LoadAdError error) {
                loadingSlots.remove(slot); android.util.Log.w("NovelexAds", "Home native: " + error.getMessage());
                if (resumed) sliderHandler.postDelayed(() -> loadNativeAd(slot), 60_000L);
            }
        }).build().loadAd(new com.google.android.gms.ads.AdRequest.Builder().build());
    }

    private void displayPendingAd(int slot, com.google.android.gms.ads.nativead.NativeAd ad) {
        if (!resumed || pendingAds.get(slot) != ad || isFinishing() || isDestroyed()) return;
        pendingAds.remove(slot);
        impressedSlots.remove(slot);
        impressionTimes.remove(slot);
        com.google.android.gms.ads.nativead.NativeAd old = nativeAds.put(slot, ad);
        if (adapter != null) adapter.notifyNativeAdChanged(slot);
        if (old != null && old != ad) old.destroy();
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
        loadRankingFromFirebase();
    }

    private void loadRankingFromFirebase() {
        FirebaseDatabase.getInstance().getReference("dramas")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@androidx.annotation.NonNull DataSnapshot snapshot) {
                    Map<String, Long> likesMap = new HashMap<>();
                    for (DataSnapshot doc : snapshot.getChildren()) {
                        String id = doc.getKey();
                        if (id == null) continue;
                        Number likes = (Number) doc.child("likes_count").getValue();
                        long count = likes != null ? likes.longValue() : 0L;
                        if (count > 0) {
                            likesMap.put(id, count);
                        }
                    }

                    new Thread(() -> {
                        List<Drama> fullCatalog = DatabaseHelper.getInstance(HomeActivity.this).getAllDramas(-1, 0);
                        if (fullCatalog.isEmpty()) return;

                        Map<String, Drama> dramaMap = new HashMap<>();
                        for (Drama d : fullCatalog) {
                            dramaMap.put(d.getId(), d);
                        }

                        // Ordenar os IDs curtidos por ordem decrescente de curtidas
                        List<Map.Entry<String, Long>> sortedLikes = new ArrayList<>(likesMap.entrySet());
                        sortedLikes.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

                        List<Drama> mostLiked = new ArrayList<>();
                        Set<String> addedIds = new HashSet<>();

                        for (Map.Entry<String, Long> entry : sortedLikes) {
                            Drama d = dramaMap.get(entry.getKey());
                            if (d != null && d.getPoster() != null && !d.getPoster().isEmpty()) {
                                mostLiked.add(d);
                                addedIds.add(d.getId());
                            }
                        }

                        // Se tiver menos de 28 títulos curtidos, completar com os mais assistidos do catálogo
                        if (mostLiked.size() < 28) {
                            List<Drama> fallback = new ArrayList<>(fullCatalog);
                            fallback.sort((a, b) -> {
                                int ep = Integer.compare(b.getEpisodesCount(), a.getEpisodesCount());
                                return ep != 0 ? ep : a.getTitle().compareToIgnoreCase(b.getTitle());
                            });
                            for (Drama d : fallback) {
                                if (!addedIds.contains(d.getId()) && d.getPoster() != null && !d.getPoster().isEmpty()) {
                                    mostLiked.add(d);
                                    addedIds.add(d.getId());
                                    if (mostLiked.size() >= 28) break;
                                }
                            }
                        }

                        // Highlights (banner carrossel topo baseado nos mais curtidos/destaque)
                        List<Drama> featured = new ArrayList<>();
                        for (Drama d : mostLiked) {
                            if (d.getBanner() != null && !d.getBanner().isEmpty()) {
                                featured.add(d);
                                if (featured.size() == 8) break;
                            }
                        }

                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            titles.clear();
                            titles.addAll(mostLiked);
                            adapter.setSectionContent(getString(R.string.home_popular), getString(mostLiked.isEmpty() ? R.string.home_empty : R.string.home_ranking_info));
                            adapter.notifyDataSetChanged();

                            if (!featured.isEmpty()) {
                                highlights.clear();
                                highlights.addAll(featured);
                                highlightAdapter.notifyDataSetChanged();
                                highlightPager.setVisibility(View.VISIBLE);
                                updateDots(0);
                            }
                        });
                    }, "ranking-processing").start();
                }

                @Override
                public void onCancelled(@androidx.annotation.NonNull DatabaseError error) {
                    android.util.Log.w("HomeActivity", "Falha ao carregar ranking de curtidas do Firebase", error.toException());
                }
            });
    }

    @Override protected void onStop() {
        resumed = false;
        sliderHandler.removeCallbacks(advanceSlide);
        super.onStop();
    }
    @Override protected void onPause() {
        resumed = false;
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        loadContinueWatching();
        loadRankingFromFirebase();
        for (Map.Entry<Integer, com.google.android.gms.ads.nativead.NativeAd> entry : new HashMap<>(pendingAds).entrySet()) {
            int slot = entry.getKey();
            long delay = Math.max(0L, impressionTimes.getOrDefault(slot, 0L) + 10_000L - android.os.SystemClock.elapsedRealtime());
            sliderHandler.postDelayed(() -> displayPendingAd(slot, entry.getValue()), delay);
        }
        for (int s = 0; s < 3; s++) {
            if (!nativeAds.containsKey(s) || impressedSlots.contains(s)) {
                loadNativeAd(s);
            }
        }
    }
    @Override protected void onDestroy() {
        sliderHandler.removeCallbacksAndMessages(null);
        for (com.google.android.gms.ads.nativead.NativeAd ad : pendingAds.values()) ad.destroy();
        for (com.google.android.gms.ads.nativead.NativeAd ad : nativeAds.values()) ad.destroy();
        pendingAds.clear(); nativeAds.clear(); super.onDestroy();
    }
}
