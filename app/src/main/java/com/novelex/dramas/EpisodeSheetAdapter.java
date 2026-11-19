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
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EpisodeSheetAdapter extends RecyclerView.Adapter<EpisodeSheetAdapter.EpisodeViewHolder> {

    public interface OnEpisodeSelectedListener {
        void onEpisodeSelected(int position, boolean isLocked);
    }

    private final Context context;
    private final String dramaId;
    private final List<Episode> episodes;
    private final int currentPlayingIndex;
    private final EpisodeAccess access;
    private final OnEpisodeSelectedListener listener;
    private final Set<Integer> downloadedEpisodes = new HashSet<>();
    private final Set<String> watchedEpisodes;
    private final int colorPink;
    private final int colorMuted;
    private final int colorWhite;

    public EpisodeSheetAdapter(Context context, String dramaId, List<Episode> episodes,
                               int currentPlayingIndex, EpisodeAccess access,
                               OnEpisodeSelectedListener listener) {
        this.context = context;
        this.dramaId = dramaId;
        this.episodes = episodes;
        this.currentPlayingIndex = currentPlayingIndex;
        this.access = access;
        this.listener = listener;

        this.colorPink = context.getColor(R.color.novelex_primary_pink);
        this.colorMuted = context.getColor(R.color.novelex_text_muted);
        this.colorWhite = context.getColor(R.color.novelex_text_white);

        this.watchedEpisodes = context.getSharedPreferences("novelex_prefs", Context.MODE_PRIVATE)
            .getStringSet("review_completed_episodes", Collections.emptySet());

        // Preload downloaded files in directory once to avoid disk I/O in onBindViewHolder
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

    @NonNull
    @Override
    public EpisodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sheet_episode, parent, false);
        return new EpisodeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) {
        Episode ep = episodes.get(position);

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
            if (position == currentPlayingIndex) {
                holder.container.setBackgroundResource(R.drawable.bg_sheet_episode_button_active);
            } else {
                holder.container.setBackgroundResource(R.drawable.bg_sheet_episode_button);
            }
        }

        if (isLocked) {
            holder.episodeState.setText(R.string.episode_state_1);
        } else if (position == currentPlayingIndex) {
            holder.episodeState.setText(R.string.episode_state_0);
        } else if (watchedEpisodes.contains(dramaId + ":" + position)) {
            holder.episodeState.setText(R.string.episode_watched);
        } else {
            holder.episodeState.setText(R.string.episode_state_2);
        }

        holder.itemView.setSelected(position == currentPlayingIndex);
        holder.btnText.setTextColor(position == currentPlayingIndex && !isLocked
            ? colorPink : isLocked ? colorMuted : colorWhite);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEpisodeSelected(position, isLocked);
            }
        });

        holder.itemView.setContentDescription("Episódio " + ep.getNumber()
            + (isLocked ? ", bloqueado" : position == currentPlayingIndex ? ", reproduzindo agora" : "")
            + (isDownloaded ? ", disponível offline" : ""));
    }

    @Override
    public int getItemCount() {
        return episodes != null ? episodes.size() : 0;
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
}
