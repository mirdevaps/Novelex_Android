package com.novelex.dramas;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.NonNull;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.nativead.NativeAd;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private PlayStoreFlows playStoreFlows;
    private CatalogInterstitial catalogInterstitial;
    private RecyclerView mainRecyclerView;
    private EditText searchEditText;
    private View topBarContainer;
    private TextView tabShortDramas;
    private TextView tabDoramas;
    private boolean showingDoramas;
    private boolean catalogTransitionRunning;
    private View[] bottomNavItems;
    private TextView[] bottomNavLabels;
    private android.widget.ImageView[] bottomNavIcons;
    private HorizontalScrollView topMenuScroll;
    private int selectedBottomNav = 0;
    private int currentPivotIndex = 0;

    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;
    private MainCombinedAdapter mainAdapter;
    private final Map<Integer, NativeAd> pendingReplacementAds = new HashMap<>();
    private final Map<Integer, Long> impressionTimes = new HashMap<>();
    private final Map<Integer, NativeAd> nativeAdsBySlot = new HashMap<>();
    private final Set<Integer> nativeAdSlotsWithImpression = new HashSet<>();
    private final Set<Integer> nativeAdRequestsInFlight = new HashSet<>();
    private final Set<Integer> pendingNativeAdSlots = new HashSet<>();
    private final Handler mainAdHandler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Runnable> nativeAdRefreshTasks = new HashMap<>();
    private final Map<Integer, Long> nativeAdRefreshDeadlines = new HashMap<>();
    private final Map<Integer, Long> nativeAdRefreshRemaining = new HashMap<>();
    private boolean activityResumed;
    private boolean mobileAdsInitialized;
    private final List<Drama> displayedDramas = new ArrayList<>();

    private static final String MAIN_NATIVE_AD_UNIT_ID = "ca-app-pub-3999190888486548/4603712451";

    // "Dublados" adicionado em destaque logo após "Todos"
    private final List<String> shortDramaCategories = new ArrayList<>(Arrays.asList(
        "Todos", "Dublados", "Histórias da Ásia", "Identidade Escondida", "Bilionários & CEOs", "Vingança", "Romance", "Lançamentos"
    ));
    private final List<String> doramaCategories = Arrays.asList("Todos", "Dublados");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        dbHelper = DatabaseHelper.getInstance(this);
        prefs = getSharedPreferences("novelex_prefs", MODE_PRIVATE);

        initViews();
        setupWindowInsets();
        setupRecyclerView();
        catalogInterstitial = new CatalogInterstitial(this);
        playStoreFlows = new PlayStoreFlows(this);
        if (BuildConfig.DEBUG) {
            MobileAds.setRequestConfiguration(new com.google.android.gms.ads.RequestConfiguration.Builder()
                .setTestDeviceIds(java.util.Collections.singletonList("C25AC9B220BD3A44921ABFD2AEACBED7"))
                .build());
        }
        MobileAds.initialize(this, initializationStatus -> {
            mobileAdsInitialized = true;
            for (int slot : new ArrayList<>(pendingNativeAdSlots)) {
                pendingNativeAdSlots.remove(slot);
                loadNativeAdForSlot(slot);
            }
        });
        loadInitialData();
        new Thread(() -> {
            try {
                int count = ReelsCatalogSync.sync(dbHelper);
                java.util.List<String> years = dbHelper.getShortDramaYears();
                android.util.Log.i("NovelexCatalog", "API catalog synchronized: " + count);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    shortDramaCategories.removeIf(c -> c.matches("[0-9]{4}"));
                    shortDramaCategories.addAll(years);
                    if (!showingDoramas && selectedBottomNav == 0 && searchEditText.length() == 0) {
                        mainAdapter.setCategories(shortDramaCategories);
                        loadCatalog();
                    }
                });
            } catch (Exception error) {
                android.util.Log.w("NovelexCatalog", "Using cached catalog; API sync failed", error);
            }
        }, "reels-catalog-sync").start();
        setupSearch();
        if (getIntent().getBooleanExtra("open_doramas", false)) switchCatalog(true);
        int requestedTab = getIntent().getIntExtra("open_bottom_tab", 0);
        if (requestedTab > 0) selectBottomNavigation(requestedTab);
        String requestedCategory = getIntent().getStringExtra("open_category");
        if (requestedCategory != null && !requestedCategory.isEmpty()) filterByCategory(requestedCategory);
        if (getIntent().getBooleanExtra("focus_search", false)) startActivity(new Intent(this, GlobalSearchActivity.class));
    }

    private void initViews() {
        topBarContainer = findViewById(R.id.topBarContainer);
        TextView brand = findViewById(R.id.pageTitle);
        android.text.SpannableString brandText = new android.text.SpannableString("NOVELEX");
        brandText.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.novelex_text_white)), 0, 4, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        brandText.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.novelex_primary_pink)), 4, 7, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        brand.setText(brandText);
        findViewById(R.id.mainGlobalSearch).setOnClickListener(v -> startActivity(new Intent(this, GlobalSearchActivity.class)));
        mainRecyclerView = findViewById(R.id.mainRecyclerView);
        searchEditText = findViewById(R.id.searchEditText);
        tabShortDramas = findViewById(R.id.tabShortDramas);
        tabDoramas = findViewById(R.id.tabDoramas);
        tabShortDramas.setOnClickListener(v -> moveToPivot(0));
        tabDoramas.setOnClickListener(v -> moveToPivot(1));
        setupBottomNavigation();
        setupAlphabetRail();
        updateCatalogTabs();

    }

    private void jumpToLetter(char letter) {
        for (int i = 0; i < displayedDramas.size(); i++) {
            String title = java.text.Normalizer.normalize(displayedDramas.get(i).getTitle(),
                java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toUpperCase(java.util.Locale.ROOT);
            if (!title.isEmpty() && (letter == '#' ? !Character.isLetter(title.charAt(0)) : title.charAt(0) == letter)) {
                ((GridLayoutManager) mainRecyclerView.getLayoutManager()).scrollToPositionWithOffset(mainAdapter.positionForDrama(i), 0);
                return;
            }
        }
    }

    private void setupAlphabetRail() {
        android.widget.LinearLayout rail = findViewById(R.id.alphabetRail);
        String letters = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        float density = getResources().getDisplayMetrics().density;
        TextView preview = new TextView(this);
        preview.setTextSize(34); preview.setGravity(android.view.Gravity.CENTER);
        preview.setTextColor(getColor(R.color.novelex_primary_pink));
        preview.setBackgroundResource(R.drawable.bg_category_minicard_selected);
        preview.setVisibility(View.GONE);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        android.widget.RelativeLayout.LayoutParams params = new android.widget.RelativeLayout.LayoutParams((int)(72*density), (int)(72*density));
        params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
        params.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        params.rightMargin = (int)(42*density);
        ((android.widget.RelativeLayout)findViewById(R.id.mainRootLayout)).addView(preview, params);
        for (char letter : letters.toCharArray()) {
            TextView item = new TextView(this);
            item.setText(String.valueOf(letter)); item.setTextSize(10);
            item.setTextColor(getColor(R.color.novelex_primary_pink));
            item.setGravity(android.view.Gravity.CENTER); item.setContentDescription("Ir para " + letter);
            rail.addView(item, new android.widget.LinearLayout.LayoutParams(-1, 0, 1f));
            item.setOnClickListener(v -> jumpToLetter(letter));
        }
        final int[] selected = {-1};
        View.OnTouchListener drag = (v, event) -> {
            float y = event.getRawY();
            int[] location = new int[2]; rail.getLocationOnScreen(location);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                rail.getParent().requestDisallowInterceptTouchEvent(true);
                float available = Math.max(1, rail.getHeight() - rail.getPaddingTop() - rail.getPaddingBottom());
                int index = Math.max(0, Math.min(letters.length()-1, (int)((y-location[1]-rail.getPaddingTop()) / available * letters.length())));
                if (selected[0] != index) {
                    if (selected[0] >= 0) rail.getChildAt(selected[0]).animate().scaleX(1).scaleY(1).setDuration(100).start();
                    selected[0] = index;
                    rail.getChildAt(index).animate().scaleX(1.45f).scaleY(1.45f).setDuration(100).start();
                    preview.animate().cancel(); preview.setText(String.valueOf(letters.charAt(index)));
                    if (preview.getVisibility() != View.VISIBLE) {
                        preview.setAlpha(0); preview.setScaleX(0.7f); preview.setScaleY(0.7f); preview.setVisibility(View.VISIBLE);
                    }
                    preview.animate().alpha(1).scaleX(1).scaleY(1).setDuration(130).start();
                    preview.setTranslationY(Math.max(-rail.getHeight()/2f+50*density, Math.min(rail.getHeight()/2f-50*density, y-location[1]-rail.getHeight()/2f)));
                    jumpToLetter(letters.charAt(index));
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (selected[0] >= 0) rail.getChildAt(selected[0]).animate().scaleX(1).scaleY(1).setDuration(120).start();
                selected[0] = -1;
                preview.animate().alpha(0).scaleX(0.8f).scaleY(0.8f).setDuration(160).withEndAction(() -> preview.setVisibility(View.GONE)).start();
                rail.getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            return false;
        };
        rail.setOnTouchListener(drag);
        for (int i=0; i<rail.getChildCount(); i++) rail.getChildAt(i).setOnTouchListener(drag);
    }

    private void setupBottomNavigation() {
        topMenuScroll = null;
        findViewById(R.id.navHome).setOnClickListener(v -> {
            Intent home = new Intent(this, HomeActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
            finish();
        });
        bottomNavItems = new View[]{
            findViewById(R.id.navMyList), findViewById(R.id.navContinue)
        };
        bottomNavLabels = new TextView[]{
            findViewById(R.id.navMyListLabel), findViewById(R.id.navContinueLabel)
        };
        bottomNavIcons = new android.widget.ImageView[bottomNavItems.length];
        for (int i = 0; i < bottomNavItems.length; i++) {
            bottomNavIcons[i] = (android.widget.ImageView) ((android.widget.LinearLayout) bottomNavItems[i]).getChildAt(0);
        }
        for (int i = 0; i < bottomNavItems.length; i++) {
            final int tab = i;
            bottomNavItems[i].setOnClickListener(v -> selectBottomNavigation(tab + 1));
        }
        updateBottomNavigationSelection();
    }

    private void selectBottomNavigation(int tab) {
        selectedBottomNav = tab;
        currentPivotIndex = showingDoramas ? 1 : 0;
        updateCatalogTabs();
        updateBottomNavigationSelection();
        revealPivot(currentPivotIndex);
        if (tab == 3) {
            showProfileDialog();
            return;
        }
        mainAdapter.setCategories(tab == 0
            ? (showingDoramas ? doramaCategories : shortDramaCategories)
            : java.util.Collections.singletonList("Todos"));
        mainRecyclerView.scrollToPosition(0);
        loadCatalog();
    }

    private void updateBottomNavigationSelection() {
        if (bottomNavItems == null) return;
        for (int i = 0; i < bottomNavItems.length; i++) {
            boolean selected = selectedBottomNav == i + 1;
            int color = getColor(selected ? R.color.novelex_primary_pink : R.color.novelex_text_muted);
            bottomNavItems[i].setBackgroundResource(R.drawable.bg_catalog_tab_idle);
            bottomNavLabels[i].setTextColor(color);
            bottomNavLabels[i].setTextSize(11);
            bottomNavLabels[i].setTypeface(null, android.graphics.Typeface.NORMAL);
            bottomNavIcons[i].setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
            bottomNavIcons[i].setScaleX(1f);
            bottomNavIcons[i].setScaleY(1f);
        }
    }

    private void showProfileDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Seu Novelex")
            .setMessage("Salve histórias pelos favoritos e continue assistindo de onde parou.\n\n" +
                "A sua lista e o seu progresso ficam neste aparelho.")
            .setPositiveButton("Voltar ao catálogo", (dialog, which) -> selectBottomNavigation(0))
            .setNegativeButton(getString(R.string.ui_41), null)
            .show();
    }

    private void moveToPivot(int pivotIndex) {
        int target = Math.max(0, Math.min(1, pivotIndex));
        if (target == currentPivotIndex && selectedBottomNav == 0) return;
        switchCatalog(target == 1);
    }

    private void revealPivot(int pivotIndex) {
        if (topMenuScroll == null || pivotIndex > 1) return;
        View item = pivotIndex == 0 ? tabShortDramas : tabDoramas;
        int targetScrollX = Math.max(0, item.getRight() - topMenuScroll.getWidth()
            + (int) (24 * getResources().getDisplayMetrics().density));
        topMenuScroll.post(() -> topMenuScroll.smoothScrollTo(targetScrollX, 0));
    }

    private void setupWindowInsets() {
        View root = findViewById(R.id.mainRootLayout);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int bottom = Math.max(safe.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            v.setPadding(safe.left, safe.top, safe.right, bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        showSystemBars();
    }

    private void showSystemBars() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) controller.show(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) showSystemBars();
    }

    private void setupRecyclerView() {
        final int columns = getResources().getInteger(R.integer.catalog_columns);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, columns);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return mainAdapter.isFullSpan(position) ? columns : 1;
            }
        });

        mainRecyclerView.setLayoutManager(gridLayoutManager);
        mainRecyclerView.setHasFixedSize(true);
        mainRecyclerView.setItemViewCacheSize(20);

        mainRecyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private float startX;
            private float startY;
            private boolean pivotGestureIntercepted;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    startX = event.getX();
                    startY = event.getY();
                    pivotGestureIntercepted = false;
                    return false;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !pivotGestureIntercepted) {
                    float deltaX = event.getX() - startX;
                    float deltaY = event.getY() - startY;
                    float threshold = getResources().getDisplayMetrics().density * 42f;
                    if (Math.abs(deltaX) >= threshold && Math.abs(deltaX) > Math.abs(deltaY) * 1.35f) {
                        pivotGestureIntercepted = true;
                        moveToPivot(currentPivotIndex + (deltaX < 0f ? 1 : -1));
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    pivotGestureIntercepted = false;
                }
            }
        });

        mainAdapter = new MainCombinedAdapter(this, displayedDramas, shortDramaCategories,
            this::openDramaPlayer, this::filterByCategory, nativeAdsBySlot, this::loadNativeAdForSlot);
        mainRecyclerView.setAdapter(mainAdapter);
    }

    private void loadNativeAdForSlot(int slotIndex) {
        if (slotIndex < 0 || isFinishing() || isDestroyed()) return;
        if (pendingReplacementAds.containsKey(slotIndex)) return;
        if (nativeAdsBySlot.containsKey(slotIndex) && !nativeAdSlotsWithImpression.contains(slotIndex)) return;
        if (nativeAdRefreshTasks.containsKey(slotIndex) || nativeAdRefreshRemaining.containsKey(slotIndex)) return;
        if (!activityResumed || !mobileAdsInitialized) {
            pendingNativeAdSlots.add(slotIndex);
            return;
        }
        if (!nativeAdRequestsInFlight.add(slotIndex)) return;
        final NativeAd[] loadedAd = new NativeAd[1];
        AdLoader loader = new AdLoader.Builder(this, MAIN_NATIVE_AD_UNIT_ID)
            .forNativeAd(nativeAd -> {
                if (isFinishing() || isDestroyed()) {
                    nativeAdRequestsInFlight.remove(slotIndex);
                    nativeAd.destroy();
                    return;
                }
                loadedAd[0] = nativeAd;
                nativeAdRequestsInFlight.remove(slotIndex);
                pendingReplacementAds.put(slotIndex, nativeAd);
                schedulePendingAdDisplay(slotIndex, nativeAd);
            })
            .withAdListener(new AdListener() {
                @Override
                public void onAdImpression() {
                    NativeAd impressionAd = loadedAd[0];
                    if (impressionAd == null || impressionAd != nativeAdsBySlot.get(slotIndex)
                        || !nativeAdSlotsWithImpression.add(slotIndex)) return;
                    impressionTimes.put(slotIndex, SystemClock.elapsedRealtime());
                    loadNativeAdForSlot(slotIndex);
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    android.util.Log.w("NovelexAds", "MainActivity.java load: " + error);
                    nativeAdRequestsInFlight.remove(slotIndex);
                    if (!nativeAdsBySlot.containsKey(slotIndex) || nativeAdSlotsWithImpression.contains(slotIndex)) {
                        scheduleNativeAdRefresh(slotIndex, 60_000L);
                    }
                }
            })
            .build();
        loader.loadAd(new AdRequest.Builder().build());
    }

    private void schedulePendingAdDisplay(int slotIndex, NativeAd nativeAd) {
        if (!activityResumed) return;
        long delay = Math.max(0L, impressionTimes.getOrDefault(slotIndex, 0L) + 10_000L - SystemClock.elapsedRealtime());
        mainAdHandler.postDelayed(() -> {
            if (!activityResumed || isFinishing() || isDestroyed()
                || pendingReplacementAds.get(slotIndex) != nativeAd) return;
            pendingReplacementAds.remove(slotIndex);
            cancelNativeAdRefresh(slotIndex);
            nativeAdSlotsWithImpression.remove(slotIndex);
            impressionTimes.remove(slotIndex);
            NativeAd previous = nativeAdsBySlot.put(slotIndex, nativeAd);
            if (mainAdapter != null) mainAdapter.notifyNativeAdChanged(slotIndex);
            if (previous != null && previous != nativeAd) previous.destroy();
        }, delay);
    }

    private void scheduleNativeAdRefresh(int slotIndex, long delayMillis) {
        cancelNativeAdRefresh(slotIndex);
        nativeAdRefreshRemaining.put(slotIndex, delayMillis);
        if (!activityResumed) {
            return;
        }
        nativeAdRefreshDeadlines.put(slotIndex, SystemClock.elapsedRealtime() + delayMillis);
        Runnable task = () -> {
            nativeAdRefreshTasks.remove(slotIndex);
            nativeAdRefreshDeadlines.remove(slotIndex);
            nativeAdRefreshRemaining.remove(slotIndex);
            if (!nativeAdsBySlot.containsKey(slotIndex) || nativeAdSlotsWithImpression.contains(slotIndex)) {
                loadNativeAdForSlot(slotIndex);
            }
        };
        nativeAdRefreshTasks.put(slotIndex, task);
        mainAdHandler.postDelayed(task, delayMillis);
    }

    private void cancelNativeAdRefresh(int slotIndex) {
        Runnable task = nativeAdRefreshTasks.remove(slotIndex);
        if (task != null) mainAdHandler.removeCallbacks(task);
        nativeAdRefreshDeadlines.remove(slotIndex);
        nativeAdRefreshRemaining.remove(slotIndex);
    }

    private void loadInitialData() {
        loadCatalog();
    }

    private void switchCatalog(boolean doramas) {
        if (catalogTransitionRunning) return;
        currentPivotIndex = doramas ? 1 : 0;
        selectedBottomNav = 0;
        updateCatalogTabs();
        updateBottomNavigationSelection();
        revealPivot(currentPivotIndex);
        if (showingDoramas == doramas) {
            mainAdapter.setCategories(doramas ? doramaCategories : shortDramaCategories);
            loadCatalog();
            return;
        }
        catalogTransitionRunning = true;
        final float direction = doramas ? 1f : -1f;
        float offset = getResources().getDisplayMetrics().density * 22f;
        mainRecyclerView.animate().cancel();
        mainRecyclerView.animate()
            .alpha(0.72f)
            .translationX(-direction * offset)
            .setDuration(110)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .withEndAction(() -> {
                showingDoramas = doramas;
                updateCatalogTabs();
                mainAdapter.setDoramaCatalog(doramas);
                if (searchEditText.getVisibility() == View.VISIBLE) searchEditText.setText("");
                mainAdapter.setCategories(selectedBottomNav == 0
                    ? (doramas ? doramaCategories : shortDramaCategories)
                    : java.util.Collections.singletonList("Todos"));
                mainRecyclerView.scrollToPosition(0);
                mainRecyclerView.setTranslationX(direction * offset);
                loadCatalog();
                mainRecyclerView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(250)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> catalogTransitionRunning = false)
                    .start();
            })
            .start();
    }

    private void updateCatalogTabs() {
        if (tabShortDramas == null || tabDoramas == null) return;
        boolean showRail = showingDoramas && selectedBottomNav == 0;
        findViewById(R.id.alphabetRail).setVisibility(showRail ? View.VISIBLE : View.GONE);
        mainRecyclerView.setPadding(mainRecyclerView.getPaddingLeft(), mainRecyclerView.getPaddingTop(),
            showRail ? (int) (32 * getResources().getDisplayMetrics().density) : 0, mainRecyclerView.getPaddingBottom());
        boolean shortSelected = selectedBottomNav == 0 && currentPivotIndex == 0;
        boolean doramaSelected = selectedBottomNav == 0 && currentPivotIndex == 1;
        tabShortDramas.animate().cancel();
        tabShortDramas.setBackgroundResource(R.drawable.bg_catalog_tab_idle);
        tabShortDramas.setTextColor(getColor(shortSelected
            ? R.color.novelex_primary_pink : R.color.novelex_text_muted));
        tabShortDramas.setScaleX(1f);
        tabShortDramas.setScaleY(1f);
        tabDoramas.animate().cancel();
        tabDoramas.setBackgroundResource(R.drawable.bg_catalog_tab_idle);
        tabDoramas.setTextColor(getColor(doramaSelected
            ? R.color.novelex_primary_pink : R.color.novelex_text_muted));
        tabDoramas.setScaleX(1f);
        tabDoramas.setScaleY(1f);
        tabShortDramas.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(tabShortDramas.getCurrentTextColor()));
        tabDoramas.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(tabDoramas.getCurrentTextColor()));
    }

    private void loadCatalog() {
        final boolean doramaCatalog = showingDoramas;
        final int bottomTab = selectedBottomNav;
        new Thread(() -> {
            List<Drama> dramas = (bottomTab == 1 || bottomTab == 2)
                ? dbHelper.getAllDramas(-1, 0)
                : dbHelper.getDramasForCatalog(doramaCatalog, doramaCatalog ? -1 : 1200, 0);
            if (doramaCatalog) {
                java.text.Collator collator = java.text.Collator.getInstance(new java.util.Locale("pt", "BR"));
                dramas.sort((a, b) -> collator.compare(a.getTitle(), b.getTitle()));
            }
            applyBottomNavigationFilter(dramas, bottomTab);
            runOnUiThread(() -> {
                if (showingDoramas != doramaCatalog || selectedBottomNav != bottomTab) return;
                displayedDramas.clear();
                displayedDramas.addAll(dramas);
                mainAdapter.setHeroDrama(dramas.isEmpty() ? null : dramas.get(0));
                updateCollectionHeader(dramas, bottomTab, doramaCatalog);
                mainAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void applyBottomNavigationFilter(List<Drama> dramas, int bottomTab) {
        if (bottomTab == 1) {
            dramas.removeIf(drama -> !prefs.getBoolean("fav_" + drama.getId(), false));
        } else if (bottomTab == 2) {
            dramas.removeIf(drama -> prefs.getLong("last_watched_" + drama.getId(), 0L) == 0L);
            dramas.sort((first, second) -> Long.compare(
                prefs.getLong("last_watched_" + second.getId(), 0L),
                prefs.getLong("last_watched_" + first.getId(), 0L)));
        }
    }

    private void updateCollectionHeader(List<Drama> dramas, int bottomTab, boolean doramas) {
        if (bottomTab == 1) {
            mainAdapter.setSectionContent(getString(R.string.ui_26), dramas.isEmpty()
                ? getString(R.string.ui_27)
                : dramas.size() + getString(R.string.ui_56));
        } else if (bottomTab == 2) {
            mainAdapter.setSectionContent(getString(R.string.ui_28), dramas.isEmpty()
                ? getString(R.string.ui_29)
                : getString(R.string.ui_30));
        } else {
            mainAdapter.setSectionContent(doramas ? getString(R.string.ui_2) : getString(R.string.ui_24),
                doramas ? dramas.size() + getString(R.string.ui_55)
                    : getString(R.string.ui_25));
        }
    }

    private void filterByCategory(String category) {
        if (selectedBottomNav != 0) return;
        final boolean doramaCatalog = showingDoramas;
        new Thread(() -> {
            String cleanCat = category.replace("🎙️", "").trim();
            List<Drama> filtered = dbHelper.getDramasByCategory(cleanCat, doramaCatalog);
            runOnUiThread(() -> {
                if (showingDoramas != doramaCatalog) return;
                displayedDramas.clear();
                displayedDramas.addAll(filtered);
                mainAdapter.setHeroDrama(filtered.isEmpty() ? null : filtered.get(0));
                updateCollectionHeader(filtered, 0, doramaCatalog);
                mainAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString();
                final boolean doramaCatalog = showingDoramas;
                final int bottomTab = selectedBottomNav;
                new Thread(() -> {
                    List<Drama> results;
                    if (bottomTab == 1 || bottomTab == 2) {
                        results = dbHelper.getAllDramas(-1, 0);
                        String query = q.trim().toLowerCase(java.util.Locale.ROOT);
                        results.removeIf(d -> !(d.getTitle() + " " + d.getSynopsis() + " " + d.getGenre()).toLowerCase(java.util.Locale.ROOT).contains(query));
                    } else {
                        results = dbHelper.searchDramas(q, doramaCatalog);
                    }
                    applyBottomNavigationFilter(results, bottomTab);
                    runOnUiThread(() -> {
                        if (showingDoramas != doramaCatalog || selectedBottomNav != bottomTab) return;
                        displayedDramas.clear();
                        displayedDramas.addAll(results);
                        mainAdapter.setHeroDrama(results.isEmpty() ? null : results.get(0));
                        updateCollectionHeader(results, bottomTab, doramaCatalog);
                        mainAdapter.notifyDataSetChanged();
                    });
                }).start();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void openDramaPlayer(Drama drama) {
        if (drama.getId().startsWith("dorama_")) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(drama.getTitle())
                .setMessage("Categorias: " + drama.getGenre() + "\nÁudio: " + drama.getAudio()
                    + "\n\nSinopse\n" + drama.getSynopsis())
                .setPositiveButton(getString(R.string.ui_39), (dialog, which) -> startDramaPlayback(drama))
                .setNegativeButton(getString(R.string.ui_40), null)
                .show();
            return;
        }
        startDramaPlayback(drama);
    }

    private void startDramaPlayback(Drama drama) {
        new Thread(() -> {
            if (drama.getEpisodes().isEmpty()) {
                List<Episode> eps = dbHelper.getEpisodesForDrama(drama.getId());
                drama.setEpisodes(eps);
            }
            runOnUiThread(() -> {
                Intent intent = new Intent(this, DramaPlayerActivity.class);
                intent.putExtra("drama", drama);
                intent.putExtra("start_episode_index",
                    prefs.getInt("last_episode_index_" + drama.getId(), 0));
                startActivity(intent);
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playStoreFlows != null) playStoreFlows.check();
        activityResumed = true;
        if (catalogInterstitial != null) catalogInterstitial.resume();
        for (Map.Entry<Integer, NativeAd> entry : new HashMap<>(pendingReplacementAds).entrySet()) {
            schedulePendingAdDisplay(entry.getKey(), entry.getValue());
        }
        for (int slot : new ArrayList<>(pendingNativeAdSlots)) {
            pendingNativeAdSlots.remove(slot);
            loadNativeAdForSlot(slot);
        }
        if (mainAdapter != null && (selectedBottomNav == 1 || selectedBottomNav == 2)) {
            if (searchEditText.length() > 0) searchEditText.setText(searchEditText.getText().toString());
            else loadCatalog();
        }
        for (Map.Entry<Integer, Long> entry : new HashMap<>(nativeAdRefreshRemaining).entrySet()) {
            int slot = entry.getKey();
            if (!nativeAdRequestsInFlight.contains(slot)
                && (!nativeAdsBySlot.containsKey(slot) || nativeAdSlotsWithImpression.contains(slot))) {
                scheduleNativeAdRefresh(slot, entry.getValue());
            }
        }
    }

    @Override
    protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PlayStoreFlows.UPDATE_REQUEST && playStoreFlows != null) playStoreFlows.updateResult(result);
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        if (catalogInterstitial != null) catalogInterstitial.pause();
        for (Map.Entry<Integer, Runnable> entry : new HashMap<>(nativeAdRefreshTasks).entrySet()) {
            int slot = entry.getKey();
            long deadline = nativeAdRefreshDeadlines.getOrDefault(slot, SystemClock.elapsedRealtime());
            nativeAdRefreshRemaining.put(slot, Math.max(0L, deadline - SystemClock.elapsedRealtime()));
            mainAdHandler.removeCallbacks(entry.getValue());
        }
        nativeAdRefreshTasks.clear();
        nativeAdRefreshDeadlines.clear();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mainAdHandler.removeCallbacksAndMessages(null);
        for (NativeAd ad : pendingReplacementAds.values()) ad.destroy();
        pendingReplacementAds.clear();
        for (Runnable task : nativeAdRefreshTasks.values()) mainAdHandler.removeCallbacks(task);
        nativeAdRefreshTasks.clear();
        nativeAdRefreshDeadlines.clear();
        for (NativeAd ad : nativeAdsBySlot.values()) ad.destroy();
        nativeAdsBySlot.clear();
        super.onDestroy();
    }
}
