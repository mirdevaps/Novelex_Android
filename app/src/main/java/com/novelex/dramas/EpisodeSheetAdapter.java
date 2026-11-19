package com.novelex.dramas;

import android.content.Context;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EpisodeSheetAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnEpisodeSelectedListener {
        void onEpisodeSelected(int position, boolean isLocked);
    }

    public interface OnNativeAdSlotRequestedListener {
        void onNativeAdSlotRequested(int slotIndex);
    }

    private static final int TYPE_EPISODE = 0;
    private static final int TYPE_NATIVE_AD = 1;

    private static final int FIRST_AD_AFTER_EPS = 5;
    private static final int EPS_BETWEEN_ADS = 10;
    private static final int MAX_NATIVE_ADS = 3;

    private final Context context;
    private final String dramaId;
    private final List<Episode> episodes;
    private final int currentPlayingIndex;
    private final EpisodeAccess access;
    private final Map<Integer, NativeAd> nativeAds;
    private final OnNativeAdSlotRequestedListener nativeAdSlotListener;
    private final OnEpisodeSelectedListener listener;
    private final Set<Integer> downloadedEpisodes = new HashSet<>();
    private final Set<String> watchedEpisodes;
    private final int colorPink;
    private final int colorMuted;
    private final int colorWhite;

    public EpisodeSheetAdapter(Context context, String dramaId, List<Episode> episodes,
                               int currentPlayingIndex, EpisodeAccess access,
                               OnEpisodeSelectedListener listener) {
        this(context, dramaId, episodes, currentPlayingIndex, access, null, null, listener);
    }

    public EpisodeSheetAdapter(Context context, String dramaId, List<Episode> episodes,
                               int currentPlayingIndex, EpisodeAccess access,
                               Map<Integer, NativeAd> nativeAds,
                               OnNativeAdSlotRequestedListener nativeAdSlotListener,
                               OnEpisodeSelectedListener listener) {
        this.context = context;
        this.dramaId = dramaId;
        this.episodes = episodes;
        this.currentPlayingIndex = currentPlayingIndex;
        this.access = access;
        this.nativeAds = nativeAds;
        this.nativeAdSlotListener = nativeAdSlotListener;
        this.listener = listener;

        this.colorPink = context.getColor(R.color.novelex_primary_pink);
        this.colorMuted = context.getColor(R.color.novelex_text_muted);
        this.colorWhite = context.getColor(R.color.novelex_text_white);

        this.watchedEpisodes = context.getSharedPreferences("novelex_prefs", Context.MODE_PRIVATE)
            .getStringSet("review_completed_episodes", Collections.emptySet());

        try {
            File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "novelex_" + dramaId);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName();
                        if (name.startsWith("ep_") && name.endsWith(".ts") && f.length() > 50000) {
                            try {
                                String numStr = name.substring(3, name.length() - 3);
                                downloadedEpisodes.add(Integer.parseInt(numStr));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public int availableAdSlotCount() {
        if (nativeAdSlotListener == null || episodes == null || episodes.size() < FIRST_AD_AFTER_EPS) return 0;
        int count = 1 + (episodes.size() - FIRST_AD_AFTER_EPS) / EPS_BETWEEN_ADS;
        return Math.min(MAX_NATIVE_ADS, count);
    }

    public int adapterPositionForAdSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= availableAdSlotCount()) return -1;
        int episodesBefore = FIRST_AD_AFTER_EPS + slotIndex * EPS_BETWEEN_ADS;
        return episodesBefore + slotIndex;
    }

    public int nativeAdSlotAtPosition(int position) {
        for (int slot = 0; slot < availableAdSlotCount(); slot++) {
            if (adapterPositionForAdSlot(slot) == position) return slot;
        }
        return -1;
    }

    public int episodeIndexAtPosition(int position) {
        int adsBefore = 0;
        for (int slot = 0; slot < availableAdSlotCount(); slot++) {
            if (adapterPositionForAdSlot(slot) < position) adsBefore++;
            else break;
        }
        return position - adsBefore;
    }

    public int positionForEpisode(int episodeIndex) {
        int position = episodeIndex;
        for (int slot = 0; slot < availableAdSlotCount(); slot++) {
            if (FIRST_AD_AFTER_EPS + slot * EPS_BETWEEN_ADS <= episodeIndex) {
                position++;
            }
        }
        return position;
    }

    public void notifyNativeAdChanged(int slotIndex) {
        int position = adapterPositionForAdSlot(slotIndex);
        if (position >= 0 && position < getItemCount()) {
            notifyItemChanged(position);
        }
    }

    @Override
    public int getItemCount() {
        int epCount = episodes != null ? episodes.size() : 0;
        return epCount + availableAdSlotCount();
    }

    @Override
    public int getItemViewType(int position) {
        return nativeAdSlotAtPosition(position) >= 0 ? TYPE_NATIVE_AD : TYPE_EPISODE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_NATIVE_AD) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_main_native_ad, parent, false);
            return new NativeAdViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sheet_episode, parent, false);
            return new EpisodeViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder rawHolder, int position) {
        if (rawHolder instanceof NativeAdViewHolder) {
            int slotIndex = nativeAdSlotAtPosition(position);
            NativeAd ad = nativeAds != null ? nativeAds.get(slotIndex) : null;
            ((NativeAdViewHolder) rawHolder).bind(ad);
            if (ad == null && nativeAdSlotListener != null) {
                nativeAdSlotListener.onNativeAdSlotRequested(slotIndex);
            }
            return;
        }

        EpisodeViewHolder holder = (EpisodeViewHolder) rawHolder;
        int epIndex = episodeIndexAtPosition(position);
        if (epIndex < 0 || epIndex >= episodes.size()) return;
        Episode ep = episodes.get(epIndex);

        Glide.with(context)
            .load(ep.getThumbnail())
            .placeholder(R.drawable.ic_novelex_play)
            .error(R.drawable.ic_novelex_play)
            .centerCrop()
            .into(holder.thumbnail);

        holder.btnText.setText(String.format(java.util.Locale.getDefault(), "%02d", ep.getNumber()));
        holder.rowTitle.setText(ep.getTitle());

        boolean isLocked = !access.isUnlocked(ep.getNumber());
        boolean isDownloaded = downloadedEpisodes.contains(ep.getNumber());

        holder.downloadedIcon.setVisibility(isDownloaded ? View.VISIBLE : View.GONE);

        if (isLocked) {
            holder.lockIcon.setVisibility(View.VISIBLE);
            holder.container.setBackgroundResource(R.drawable.bg_sheet_episode_locked);
            holder.btnText.setTextColor(0xFF7E7A8C);
        } else {
            holder.lockIcon.setVisibility(View.GONE);
            holder.btnText.setTextColor(0xFFFFFFFF);
            if (epIndex == currentPlayingIndex) {
                holder.container.setBackgroundResource(R.drawable.bg_sheet_episode_button_active);
            } else {
                holder.container.setBackgroundResource(R.drawable.bg_sheet_episode_button);
            }
        }

        if (isLocked) {
            holder.episodeState.setText(R.string.episode_state_1);
        } else if (epIndex == currentPlayingIndex) {
            holder.episodeState.setText(R.string.episode_state_0);
        } else if (watchedEpisodes.contains(dramaId + ":" + epIndex)) {
            holder.episodeState.setText(R.string.episode_watched);
        } else {
            holder.episodeState.setText(R.string.episode_state_2);
        }

        holder.itemView.setSelected(epIndex == currentPlayingIndex);
        holder.btnText.setTextColor(epIndex == currentPlayingIndex && !isLocked
            ? colorPink : isLocked ? colorMuted : colorWhite);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEpisodeSelected(epIndex, isLocked);
            }
        });

        holder.itemView.setContentDescription("Episódio " + ep.getNumber()
            + (isLocked ? ", bloqueado" : epIndex == currentPlayingIndex ? ", reproduzindo agora" : "")
            + (isDownloaded ? ", disponível offline" : ""));
    }

    static class EpisodeViewHolder extends RecyclerView.ViewHolder {
        final View container;
        final TextView btnText;
        final ImageView lockIcon;
        final TextView downloadedIcon;
        final ImageView thumbnail;
        final TextView rowTitle;
        final TextView episodeState;

        public EpisodeViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.btnEpisodeGridContainer);
            btnText = itemView.findViewById(R.id.btnEpisodeGridItem);
            lockIcon = itemView.findViewById(R.id.txtLockIcon);
            downloadedIcon = itemView.findViewById(R.id.txtDownloadedIcon);
            thumbnail = itemView.findViewById(R.id.episodeThumbnail);
            rowTitle = itemView.findViewById(R.id.episodeRowTitle);
            episodeState = itemView.findViewById(R.id.episodeState);
        }
    }

    static class NativeAdViewHolder extends RecyclerView.ViewHolder {
        final NativeAdView adView;
        final TextView headline;
        final TextView body;
        final TextView callToAction;
        final ImageView icon;
        private NativeAd boundNativeAd;

        NativeAdViewHolder(@NonNull View itemView) {
            super(itemView);
            adView = (NativeAdView) itemView;
            headline = itemView.findViewById(R.id.nativeAdHeadline);
            body = itemView.findViewById(R.id.nativeAdBody);
            callToAction = itemView.findViewById(R.id.nativeAdCallToAction);
            icon = itemView.findViewById(R.id.nativeAdIcon);
            adView.setHeadlineView(headline);
            adView.setBodyView(body);
            adView.setCallToActionView(callToAction);
            adView.setIconView(icon);
            adView.setMediaView(itemView.findViewById(R.id.nativeAdMedia));
        }

        void bind(NativeAd nativeAd) {
            itemView.setVisibility(nativeAd == null ? View.GONE : View.VISIBLE);
            if (nativeAd == null) {
                boundNativeAd = null;
                headline.setText("");
                body.setText("");
                callToAction.setText("");
                icon.setImageDrawable(null);
                return;
            }
            if (boundNativeAd == nativeAd) return;
            boundNativeAd = nativeAd;
            headline.setText(nativeAd.getHeadline());

            if (nativeAd.getBody() != null) {
                body.setText(nativeAd.getBody());
                body.setVisibility(View.VISIBLE);
            } else {
                body.setVisibility(View.GONE);
            }

            if (nativeAd.getCallToAction() != null) {
                callToAction.setText(nativeAd.getCallToAction());
                callToAction.setVisibility(View.VISIBLE);
            } else {
                callToAction.setVisibility(View.GONE);
            }

            if (nativeAd.getIcon() != null) {
                icon.setImageDrawable(nativeAd.getIcon().getDrawable());
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.GONE);
            }

            adView.setNativeAd(nativeAd);
        }
    }
}
