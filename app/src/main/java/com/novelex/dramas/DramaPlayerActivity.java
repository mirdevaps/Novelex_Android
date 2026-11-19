package com.novelex.dramas;

import android.content.Intent;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.graphics.Color;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DramaPlayerActivity extends AppCompatActivity {

    private Drama currentDrama;
    private boolean isDoramaContent;
    private androidx.media3.ui.PlayerControlView doramaControls;
    private PlayerView singlePlayerView;
    private View touchOverlay;
    private ProgressBar playerBuffering;
    private TextView playerDramaTitle;
    private TextView playerEpisodeNumber;
    private TextView playerEpisodeTitle;
    private TextView playerDramaSynopsis;
    private TextView txtLikeCount;
    private TextView txtDownload;
    private ImageView bigHeartAnim;
    private ImageView imgPlayPauseOverlay;

    private ImageView btnBack;
    private ImageView btnLike;
    private ImageView btnFavorite;
    private ImageView btnDownload;
    private ImageView btnEpisodesList;
    private ImageView btnNextEpisode;
    private ImageView btnPrevEpisode;

    private ExoPlayer exoPlayer;
    private int currentEpisodeIndex = 0;
    private EpisodeAccess episodeAccess;
    private boolean isLiked = false;
    private boolean likeRequestInFlight;
    private com.google.firebase.firestore.ListenerRegistration likesCountListener;
    private com.google.firebase.firestore.ListenerRegistration likedStateListener;
    private boolean isFavorite = false;
    private long likesCount = 0;
    private SharedPreferences prefs;
    private GestureDetector gestureDetector;
    private boolean episodeTransitionRunning;
    private String deviceId;

    private FirebaseFirestore firestore;
    private boolean rewardedScreenActive;
    private RewardedAd rewardedAd;
    private boolean isLoadingAd = false;
    private boolean isDownloading = false;

    private static final String REWARDED_AD_UNIT_ID = "ca-app-pub-3437419183521595/1686247958";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        currentDrama = (Drama) getIntent().getSerializableExtra("drama");
        if (currentDrama == null || currentDrama.getEpisodes().isEmpty()) {
            Toast.makeText(this, getString(R.string.ui_35), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        isDoramaContent = currentDrama.getId().startsWith("dorama_")
            || currentDrama.getGenre().toLowerCase(java.util.Locale.ROOT).contains("dorama");
        setRequestedOrientation((isDoramaContent || getResources().getConfiguration().smallestScreenWidthDp >= 600)
            ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
            : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        prefs = getSharedPreferences("novelex_prefs", MODE_PRIVATE);
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId == null || deviceId.trim().isEmpty() || "9774d56d682e549c".equals(deviceId)) {
            deviceId = prefs.getString("device_unique_uuid", null);
            if (deviceId == null) {
                deviceId = java.util.UUID.randomUUID().toString();
                prefs.edit().putString("device_unique_uuid", deviceId).apply();
            }
        }
        firestore = FirebaseFirestore.getInstance();

        episodeAccess = new EpisodeAccess(prefs, currentDrama.getId());
        isLiked = prefs.getBoolean("liked_" + currentDrama.getId(), false);
        isFavorite = prefs.getBoolean("fav_" + currentDrama.getId(), false);

        if (BuildConfig.DEBUG) {
            MobileAds.setRequestConfiguration(new com.google.android.gms.ads.RequestConfiguration.Builder()
                .setTestDeviceIds(java.util.Collections.singletonList("C25AC9B220BD3A44921ABFD2AEACBED7"))
                .build());
        }
        MobileAds.initialize(this, initializationStatus -> {});

        showSystemBars();
        initViews();
        setupExoPlayer();
        setupActions();
        setupPlayerGestures();
        syncWithFirestore();

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                exitPlayerWithAd();
            }
        });

        int initialEpisodeIndex = getIntent().getIntExtra("start_episode_index", 0);
        if (initialEpisodeIndex < 0 || initialEpisodeIndex >= currentDrama.getEpisodes().size()) {
            initialEpisodeIndex = 0;
        }
        playEpisode(initialEpisodeIndex);
    }

    private void requestAndShowRewardedAd(int targetIndex, TextView status, androidx.appcompat.app.AlertDialog dialog) {
        if (!rewardedScreenActive || isLoadingAd || isFinishing() || isDestroyed()) return;
        if (status != null) status.setText(getString(R.string.ui_33));
        isLoadingAd = true;
        android.util.Log.i("NovelexAds", "Rewarded requested by unlock action for episode index " + targetIndex);
        RewardedAd.load(this, REWARDED_AD_UNIT_ID,
            new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull RewardedAd ad) {
                    isLoadingAd = false;
                    if (!rewardedScreenActive || isFinishing() || isDestroyed()) { rewardedAd = null; return; }
                    android.util.Log.i("NovelexAds", "Rewarded loaded from explicit unlock action");
                    rewardedAd = ad;
                    ad.setFullScreenContentCallback(new com.google.android.gms.ads.FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            rewardedAd = null;
                        }
                        @Override
                        public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError error) {
                            rewardedAd = null;
                            Toast.makeText(DramaPlayerActivity.this,
                                getString(R.string.ui_57), Toast.LENGTH_LONG).show();
                        }
                    });
                    RewardedAd adToShow = rewardedAd;
                    rewardedAd = null;
                    adToShow.show(DramaPlayerActivity.this, rewardItem -> {
                        episodeAccess.grant(currentDrama.getEpisodes(), targetIndex);
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        Toast.makeText(DramaPlayerActivity.this, getString(R.string.ui_34), Toast.LENGTH_SHORT).show();
                        playEpisode(targetIndex);
                    });
                }
                @Override
                public void onAdFailedToLoad(@NonNull com.google.android.gms.ads.LoadAdError loadAdError) {
                    android.util.Log.w("NovelexAds", "Rewarded action load failed: " + loadAdError);
                    rewardedAd = null;
                    isLoadingAd = false;
                    if (status != null) status.setText(getString(R.string.ui_32));
                    Toast.makeText(DramaPlayerActivity.this, getString(R.string.ui_57), Toast.LENGTH_LONG).show();
                }
            });
    }

    private void showSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(R.id.playerRootLayout);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(root);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            showSystemBars();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!isDoramaContent && getResources().getConfiguration().smallestScreenWidthDp < 600) return;
        View playerRoot = findViewById(R.id.playerRootLayout);
        if (singlePlayerView != null) singlePlayerView.requestLayout();
        if (playerRoot != null) playerRoot.requestLayout();
        showSystemBars();
    }

    private void initViews() {
        singlePlayerView = findViewById(R.id.singlePlayerView);
        touchOverlay = findViewById(R.id.touchOverlay);
        playerBuffering = findViewById(R.id.playerBuffering);
        playerDramaTitle = findViewById(R.id.playerDramaTitle);
        playerEpisodeNumber = findViewById(R.id.playerEpisodeNumber);
        playerEpisodeTitle = findViewById(R.id.playerEpisodeTitle);
        playerDramaSynopsis = findViewById(R.id.playerDramaSynopsis);
        txtLikeCount = findViewById(R.id.txtLikeCount);
        txtDownload = findViewById(R.id.txtDownload);
        bigHeartAnim = findViewById(R.id.bigHeartAnim);
        imgPlayPauseOverlay = findViewById(R.id.imgPlayPauseOverlay);

        btnBack = findViewById(R.id.btnBack);
        btnLike = findViewById(R.id.btnLike);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnDownload = findViewById(R.id.btnDownload);
        btnEpisodesList = findViewById(R.id.btnEpisodesList);
        btnNextEpisode = findViewById(R.id.btnNextEpisode);
        btnPrevEpisode = findViewById(R.id.btnPrevEpisode);

        playerDramaTitle.setText(currentDrama.getTitle());
        String synopsis = currentDrama.getSynopsis();
        boolean hasSynopsis = synopsis != null && !synopsis.trim().isEmpty()
            && !synopsis.equals("Sinopse em breve...");
        playerDramaSynopsis.setText(currentDrama.getGenre() + (hasSynopsis ? " · " + synopsis : ""));

        btnBack.setOnClickListener(v -> exitPlayerWithAd());
        imgPlayPauseOverlay.setOnClickListener(v -> togglePlayPause());
    }

    private void exitPlayerWithAd() {
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
        CatalogInterstitial.getInstance().showOnNavigation(this, this::finish);
    }

    private void setupExoPlayer() {
        exoPlayer = new ExoPlayer.Builder(this)
            .setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                new androidx.media3.datasource.DefaultDataSource.Factory(this,
                    new androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(PlaybackNetwork.client()))))
            .setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build();
        java.util.Locale appLocale = androidx.core.os.ConfigurationCompat.getLocales(
            getResources().getConfiguration()).get(0);
        String language = appLocale == null ? "pt" : appLocale.getLanguage();
        if (!language.equals("pt") && !language.equals("en") && !language.equals("ko")) language = "pt";
        exoPlayer.setTrackSelectionParameters(exoPlayer.getTrackSelectionParameters().buildUpon()
            .setPreferredAudioLanguage(language)
            .setPreferredTextLanguage(language)
            .build());
        singlePlayerView.setPlayer(exoPlayer);
        if (getResources().getConfiguration().smallestScreenWidthDp >= 600) {
            singlePlayerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        }
        if (isDoramaContent) {
            androidx.media3.ui.PlayerControlView controls = (androidx.media3.ui.PlayerControlView)
                getLayoutInflater().inflate(R.layout.dorama_controls, (android.view.ViewGroup) findViewById(R.id.playerRootLayout), false);
            doramaControls = controls;
            controls.setPlayer(exoPlayer);
            controls.setShowTimeoutMs(3000);
            controls.setShowNextButton(false);
            controls.setShowPreviousButton(false);
            singlePlayerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            android.widget.RelativeLayout.LayoutParams params = new android.widget.RelativeLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
            ((android.widget.RelativeLayout) findViewById(R.id.playerRootLayout)).addView(controls, params);
            controls.hide();
            controls.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                for (int id : new int[]{R.id.playerBottomOverlay, R.id.sideActionsLayout}) {
                    View overlay = findViewById(id);
                    android.widget.RelativeLayout.LayoutParams p = (android.widget.RelativeLayout.LayoutParams) overlay.getLayoutParams();
                    if (p.bottomMargin != b - t) { p.bottomMargin = b - t; overlay.setLayoutParams(p); }
                }
            });
        }

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    playerBuffering.setVisibility(View.VISIBLE);
                } else {
                    playerBuffering.setVisibility(View.GONE);
                }

                if (playbackState == Player.STATE_ENDED) {
                    java.util.Set<String> completed = new java.util.HashSet<>(prefs.getStringSet(
                        "review_completed_episodes", java.util.Collections.emptySet()));
                    completed.add(currentDrama.getId() + ":" + currentEpisodeIndex);
                    prefs.edit().putStringSet("review_completed_episodes", completed).apply();
                    if (currentEpisodeIndex + 1 < currentDrama.getEpisodes().size()) {
                        int next = currentEpisodeIndex + 1;
                        if (!episodeAccess.isUnlocked(currentDrama.getEpisodes().get(next).getNumber())) {
                            requestAndShowRewardedAd(next, null, null);
                        } else {
                            playEpisode(next);
                        }
                    }
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    imgPlayPauseOverlay.setVisibility(View.GONE);
                }
            }
        });
    }

    private void checkAndPlayEpisode(int targetIndex) {
        if (targetIndex < 0 || targetIndex >= currentDrama.getEpisodes().size()) return;
        Episode ep = currentDrama.getEpisodes().get(targetIndex);

        if (!episodeAccess.isUnlocked(ep.getNumber())) {
            showUnlockAdDialog(targetIndex);
        } else {
            playEpisode(targetIndex);
        }
    }

    private void showUnlockAdDialog(int targetIndex) {
        Episode ep = currentDrama.getEpisodes().get(targetIndex);
        View content = getLayoutInflater().inflate(R.layout.dialog_unlock_episode, null);
        ((TextView) content.findViewById(R.id.unlockChapter)).setText(getString(R.string.ui_52) + ep.getNumber());
        TextView status = content.findViewById(R.id.unlockStatus);
        status.setText(getString(R.string.reward_action_prompt));
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(content).create();
        content.findViewById(R.id.unlockClose).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.unlockWatch).setOnClickListener(v -> {
            requestAndShowRewardedAd(targetIndex, status, dialog);
        });
        dialog.show();
    }

    private void playEpisode(int index) {
        if (index < 0 || index >= currentDrama.getEpisodes().size()) return;
        if (!episodeAccess.isUnlocked(currentDrama.getEpisodes().get(index).getNumber())) {
            showUnlockAdDialog(index);
            return;
        }

        currentEpisodeIndex = index;
        Episode ep = currentDrama.getEpisodes().get(index);
        prefs.edit()
            .putInt("last_episode_index_" + currentDrama.getId(), index)
            .putLong("last_watched_" + currentDrama.getId(), System.currentTimeMillis())
            .apply();

        playerEpisodeNumber.setText(getString(R.string.ui_51) + ep.getNumber());
        String episodeTitle = ep.getTitle();
        boolean genericTitle = episodeTitle.matches("(?iu).*(?:episódio|episodio|capítulo|capitulo|temporada)\\s*\\d+.*");
        playerEpisodeTitle.setText(genericTitle ? currentDrama.getTitle() : episodeTitle);

        File offlineFile = getOfflineEpisodeFile(ep.getNumber());
        String streamUrl;
        if (offlineFile.exists() && offlineFile.length() > 50000) {
            streamUrl = offlineFile.getAbsolutePath();
            txtDownload.setText(getString(R.string.ui_36));
            btnDownload.setColorFilter(0xFF00E676);
            Toast.makeText(this, getString(R.string.ui_64), Toast.LENGTH_SHORT).show();
        } else {
            streamUrl = ep.getStreamUrl();
            txtDownload.setText(getString(R.string.ui_6));
            btnDownload.setColorFilter(0xFFFFFFFF);
        }

        if (streamUrl != null && !streamUrl.isEmpty()) {
            MediaItem mediaItem = MediaItem.fromUri(streamUrl);
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.play();
            imgPlayPauseOverlay.setVisibility(View.GONE);
        }
    }

    private File getOfflineEpisodeFile(int episodeNum) {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "novelex_" + currentDrama.getId());
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "ep_" + episodeNum + ".ts");
    }

    private void downloadCurrentEpisodeOffline() {
        if (isDownloading) {
            Toast.makeText(this, getString(R.string.ui_58), Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentEpisodeIndex >= currentDrama.getEpisodes().size()) return;
        Episode ep = currentDrama.getEpisodes().get(currentEpisodeIndex);

        File target = getOfflineEpisodeFile(ep.getNumber());
        if (target.exists() && target.length() > 50000) {
            Toast.makeText(this, getString(R.string.ui_59), Toast.LENGTH_SHORT).show();
            return;
        }

        isDownloading = true;
        txtDownload.setText("0%");
        btnDownload.setColorFilter(0xFFFFD700);
        Toast.makeText(this, "Iniciando download do capítulo " + ep.getNumber() + "... 📥", Toast.LENGTH_SHORT).show();

        HlsDownloader.downloadHlsAsSingleVideo(ep.getStreamUrl(), target, new HlsDownloader.DownloadCallback() {
            @Override
            public void onProgress(int progressPercent) {
                runOnUiThread(() -> txtDownload.setText(progressPercent + "%"));
            }

            @Override
            public void onSuccess(File destinationFile) {
                isDownloading = false;
                runOnUiThread(() -> {
                    txtDownload.setText(getString(R.string.ui_36));
                    btnDownload.setColorFilter(0xFF00E676);
                    Toast.makeText(DramaPlayerActivity.this, getString(R.string.ui_53) + ep.getNumber() + " baixado com sucesso! Já está offline 🎬", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String error) {
                isDownloading = false;
                runOnUiThread(() -> {
                    txtDownload.setText(getString(R.string.ui_6));
                    btnDownload.setColorFilter(0xFFFFFFFF);
                    Toast.makeText(DramaPlayerActivity.this, "Falha no download: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // 1 Toque: Pausa ou Despausa
    private void togglePlayPause() {
        if (exoPlayer == null) return;
        if (exoPlayer.isPlaying()) {
            exoPlayer.pause();
            imgPlayPauseOverlay.setVisibility(View.VISIBLE);
        } else {
            exoPlayer.play();
            imgPlayPauseOverlay.setVisibility(View.GONE);
        }
    }

    // 2 Toques: Curte com animação de coração gigante estilo TikTok/Instagram
    private void triggerDoubleTapLike() {
        touchOverlay.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        setDramaLiked(true);

        // Animação espetacular do Coração Gigante
        bigHeartAnim.setVisibility(View.VISIBLE);
        bigHeartAnim.setScaleX(0.2f);
        bigHeartAnim.setScaleY(0.2f);
        bigHeartAnim.setAlpha(0.9f);
        bigHeartAnim.setTranslationY(0f);

        bigHeartAnim.animate()
            .scaleX(1.35f)
            .scaleY(1.35f)
            .setDuration(220)
            .setInterpolator(new OvershootInterpolator(2.0f))
            .withEndAction(() -> {
                bigHeartAnim.animate()
                    .translationY(-120f)
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        bigHeartAnim.setVisibility(View.GONE);
                        bigHeartAnim.setTranslationY(0f);
                    })
                    .start();
            })
            .start();
    }

    private void animateEpisodeSwipe(int target, boolean forward) {
        if (episodeTransitionRunning || exoPlayer == null || target < 0
            || target >= currentDrama.getEpisodes().size()) return;
        if (!episodeAccess.isUnlocked(currentDrama.getEpisodes().get(target).getNumber())) {
            showUnlockAdDialog(target);
            return;
        }
        episodeTransitionRunning = true;
        float distance = Math.min(singlePlayerView.getHeight() * 0.18f, dp(150));
        float direction = forward ? -1f : 1f;
        View caption = findViewById(R.id.playerBottomOverlay);
        caption.animate().translationY(direction * dp(20)).alpha(0f).setDuration(160).start();
        singlePlayerView.animate().cancel();
        singlePlayerView.animate().translationY(direction * distance).alpha(0f)
            .setDuration(180).setInterpolator(new android.view.animation.AccelerateInterpolator())
            .withEndAction(() -> {
                if (isFinishing() || isDestroyed() || exoPlayer == null) {
                    episodeTransitionRunning = false;
                    return;
                }
                playEpisode(target);
                singlePlayerView.setTranslationY(-direction * distance);
                caption.setTranslationY(-direction * dp(20));
                singlePlayerView.animate().translationY(0f).alpha(1f).setDuration(300)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                    .withEndAction(() -> episodeTransitionRunning = false).start();
                caption.animate().translationY(0f).alpha(1f).setDuration(300)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }).start();
    }

    private void setupPlayerGestures() {
        final boolean[] scrubbing = {false};
        final float[] originX = {0f};
        final float[] originY = {0f};
        final boolean[] verticalDrag = {false};
        final float density = getResources().getDisplayMetrics().density;
        final long[] seekPosition = {0L};
        final long[] originPosition = {0L};
        final boolean[] resumePlayback = {false};
        LiquidSeekView seekPreview = new LiquidSeekView(this);
        ((android.widget.RelativeLayout) findViewById(R.id.playerRootLayout)).addView(seekPreview,
            new android.widget.RelativeLayout.LayoutParams(-1, -1));

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 70;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onDown(MotionEvent e) {
                return true; // Essencial para que os toques subsequentes funcionem no Android
            }


            // 1 Toque: Pausa ou Despausa
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isDoramaContent && doramaControls != null) {
                    if (doramaControls.isFullyVisible()) doramaControls.hide();
                    else doramaControls.show();
                    return true;
                }
                togglePlayPause();
                return true;
            }

            // 2 Toques: Curte com animação
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                triggerDoubleTapLike();
                return true;
            }

            // Deslizar para cima / baixo troca de capítulo (TikTok style)
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();

                if (Math.abs(diffY) > Math.abs(diffX)) {
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY < 0) {
                            if (currentEpisodeIndex + 1 < currentDrama.getEpisodes().size()) {
                                animateEpisodeSwipe(currentEpisodeIndex + 1, true);
                            }
                        } else {
                            if (currentEpisodeIndex > 0) {
                                animateEpisodeSwipe(currentEpisodeIndex - 1, false);
                            }
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        gestureDetector.setIsLongpressEnabled(false);
        touchOverlay.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                originX[0] = event.getX(); originY[0] = event.getY(); verticalDrag[0] = false;
            }
            if (!isDoramaContent && !scrubbing[0] && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - originX[0], dy = event.getY() - originY[0];
                float threshold = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
                if (Math.abs(dy) > threshold && Math.abs(dy) > Math.abs(dx)) verticalDrag[0] = true;
                if (!verticalDrag[0] && Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy) * 1.3f
                    && exoPlayer != null && exoPlayer.isCurrentMediaItemSeekable() && exoPlayer.getDuration() > 0) {
                    scrubbing[0] = true;
                    originPosition[0] = exoPlayer.getCurrentPosition();
                    seekPosition[0] = originPosition[0];
                    resumePlayback[0] = exoPlayer.getPlayWhenReady();
                    exoPlayer.pause();
                    MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
                    gestureDetector.onTouchEvent(cancel); cancel.recycle();

                }
            }
            if (scrubbing[0]) {
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE && exoPlayer != null) {
                    long duration = exoPlayer.getDuration();
                    // One screen width corresponds to the full episode duration.
                    double fraction = (event.getX() - originX[0]) / Math.max(1f, v.getWidth());
                    seekPosition[0] = Math.max(0L, Math.min(duration,
                        originPosition[0] + (long) (fraction * duration)));
                    seekPreview.update(seekPosition[0] - originPosition[0], duration, seekPosition[0], event.getY());
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    boolean commit = event.getActionMasked() == MotionEvent.ACTION_UP;
                    scrubbing[0] = false;
                    seekPreview.retract();
                    if (exoPlayer != null) {
                        if (commit) exoPlayer.seekTo(seekPosition[0]);
                        exoPlayer.setPlayWhenReady(resumePlayback[0]);
                    }
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    gestureDetector.onTouchEvent(cancel);
                    cancel.recycle();
                }
                return true;
            }
            return gestureDetector.onTouchEvent(event);
        });
    }

    private void syncWithFirestore() {
        if (likesCountListener != null) likesCountListener.remove();
        if (likedStateListener != null) likedStateListener.remove();

        DocumentReference dramaRef = firestore.collection("dramas").document(currentDrama.getId());
        likesCountListener = dramaRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                android.util.Log.w("NovelexLikes", "Counter read failed: " + error.getMessage());
                return;
            }
            if (isFinishing() || isDestroyed()) return;
            Long count = snapshot != null && snapshot.exists() ? snapshot.getLong("likes_count") : null;
            if (count != null) {
                likesCount = Math.max(0L, count);
                txtLikeCount.setText(String.valueOf(likesCount));
            }
        });

        likedStateListener = firestore.collection("users").document(deviceId).collection("likes")
            .document(currentDrama.getId()).addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    android.util.Log.w("NovelexLikes", "Like state read failed: " + error.getMessage());
                    return;
                }
                if (snapshot == null || isFinishing() || isDestroyed()) return;
                if (snapshot.exists()) {
                    isLiked = true;
                    prefs.edit().putBoolean("liked_" + currentDrama.getId(), true).apply();
                    updateLikeIcon();
                }
            });

        updateLikeIcon();
        updateFavoriteIcon();
    }

    private void setDramaLiked(boolean desired) {
        if (isFinishing() || isDestroyed()) return;
        if (isLiked == desired) return;

        // Atualização Otimista Imediata na UI e Preferences
        isLiked = desired;
        prefs.edit().putBoolean("liked_" + currentDrama.getId(), desired).apply();
        updateLikeIcon();

        likesCount = Math.max(0L, likesCount + (desired ? 1 : -1));
        txtLikeCount.setText(String.valueOf(likesCount));

        DocumentReference dramaRef = firestore.collection("dramas").document(currentDrama.getId());
        DocumentReference userLikeRef = firestore.collection("users").document(deviceId)
            .collection("likes").document(currentDrama.getId());

        if (desired) {
            Map<String, Object> data = new HashMap<>();
            data.put("drama_id", currentDrama.getId());
            data.put("timestamp", FieldValue.serverTimestamp());
            userLikeRef.set(data);

            Map<String, Object> dramaData = new HashMap<>();
            dramaData.put("title", currentDrama.getTitle());
            dramaData.put("likes_count", FieldValue.increment(1));
            dramaRef.set(dramaData, SetOptions.merge());
        } else {
            userLikeRef.delete();

            Map<String, Object> dramaData = new HashMap<>();
            dramaData.put("title", currentDrama.getTitle());
            dramaData.put("likes_count", FieldValue.increment(-1));
            dramaRef.set(dramaData, SetOptions.merge());
        }
    }

    private void setupActions() {
        btnNextEpisode.setOnClickListener(v -> {
            if (currentEpisodeIndex + 1 < currentDrama.getEpisodes().size()) {
                checkAndPlayEpisode(currentEpisodeIndex + 1);
            } else {
                Toast.makeText(this, getString(R.string.ui_37), Toast.LENGTH_SHORT).show();
            }
        });

        btnPrevEpisode.setOnClickListener(v -> {
            if (currentEpisodeIndex > 0) {
                playEpisode(currentEpisodeIndex - 1);
            } else {
                Toast.makeText(this, getString(R.string.ui_38), Toast.LENGTH_SHORT).show();
            }
        });

        btnLike.setOnClickListener(v -> setDramaLiked(!isLiked));

        btnFavorite.setOnClickListener(v -> {
            isFavorite = !isFavorite;
            prefs.edit().putBoolean("fav_" + currentDrama.getId(), isFavorite).apply();
            updateFavoriteIcon();

            DocumentReference userFavRef = firestore.collection("users").document(deviceId)
                .collection("favorites").document(currentDrama.getId());

            if (isFavorite) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", currentDrama.getId());
                data.put("title", currentDrama.getTitle());
                data.put("poster", currentDrama.getPoster());
                data.put("genre", currentDrama.getGenre());
                data.put("ep_count", currentDrama.getEpisodesCount());
                data.put("timestamp", FieldValue.serverTimestamp());
                userFavRef.set(data);
                Toast.makeText(this, getString(R.string.ui_61), Toast.LENGTH_SHORT).show();
            } else {
                userFavRef.delete();
                Toast.makeText(this, getString(R.string.ui_60), Toast.LENGTH_SHORT).show();
            }
        });

        btnDownload.setOnClickListener(v -> downloadCurrentEpisodeOffline());
        btnEpisodesList.setOnClickListener(v -> openEpisodesBottomSheet());
    }

    private void updateLikeIcon() {
        btnLike.setColorFilter(isLiked ? 0xFFFF2E7E : 0xFFFFFFFF);
    }

    private void updateFavoriteIcon() {
        btnFavorite.setColorFilter(isFavorite ? 0xFFFFD700 : 0xFFFFFFFF);
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> episodeListLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                checkAndPlayEpisode(result.getData().getIntExtra("episode_index", currentEpisodeIndex));
            }
        });

    private void openEpisodesBottomSheet() {
        Intent intent = new Intent(this, EpisodeListActivity.class);
        intent.putExtra("drama", currentDrama);
        intent.putExtra("current", currentEpisodeIndex);
        episodeListLauncher.launch(intent);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        rewardedScreenActive = true;
    }

    @Override
    protected void onPause() {
        rewardedScreenActive = false;
        super.onPause();
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (likesCountListener != null) likesCountListener.remove();
        if (likedStateListener != null) likedStateListener.remove();
        super.onDestroy();
        rewardedAd = null;
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }
}
