package com.novelex.dramas;

import android.content.Context;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.List;

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

    public EpisodeSheetAdapter(Context context, String dramaId, List<Episode> episodes,
                               int currentPlayingIndex, EpisodeAccess access,
                               OnEpisodeSelectedListener listener) {
        this.context = context;
        this.dramaId = dramaId;
        this.episodes = episodes;
        this.currentPlayingIndex = currentPlayingIndex;
        this.access = access;
        this.listener = listener;
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
        ImageView thumbnail = holder.itemView.findViewById(R.id.episodeThumbnail);
        com.bumptech.glide.Glide.with(context).load(ep.getThumbnail()).placeholder(R.drawable.ic_novelex_play)
            .error(R.drawable.ic_novelex_play).centerCrop().into(thumbnail);
        holder.btnText.setText(String.format(java.util.Locale.getDefault(), "%02d", ep.getNumber()));
        ((TextView)holder.itemView.findViewById(R.id.episodeRowTitle)).setText(ep.getTitle());

        // Episódios 1 e 2 são sempre grátis. A partir daí, precisa estar desbloqueado
        boolean isLocked = !access.isUnlocked(ep.getNumber());

        // Verifica se o episódio está salvo offline no aparelho
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "novelex_" + dramaId);
        File localEpFile = new File(dir, "ep_" + ep.getNumber() + ".ts");
        boolean isDownloaded = localEpFile.exists() && localEpFile.length() > 50000;

        if (isDownloaded) {
            holder.downloadedIcon.setVisibility(View.VISIBLE);
        } else {
            holder.downloadedIcon.setVisibility(View.GONE);
        }

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

        ((TextView)holder.itemView.findViewById(R.id.episodeState)).setText(isLocked
            ? R.string.episode_state_1 : position == currentPlayingIndex ? R.string.episode_state_0 : R.string.episode_state_2);
        java.util.Set<String> watched = context.getSharedPreferences("novelex_prefs", Context.MODE_PRIVATE).getStringSet("review_completed_episodes", java.util.Collections.emptySet());
        if (!isLocked && position != currentPlayingIndex && watched.contains(dramaId + ":" + position)) {
            ((TextView)holder.itemView.findViewById(R.id.episodeState)).setText(R.string.episode_watched);
        }
        holder.itemView.setSelected(position == currentPlayingIndex);
        holder.btnText.setTextColor(position == currentPlayingIndex && !isLocked
            ? context.getColor(R.color.novelex_primary_pink) : isLocked
            ? context.getColor(R.color.novelex_text_muted) : context.getColor(R.color.novelex_text_white));
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
        View container;
        TextView btnText;
        ImageView lockIcon;
        TextView downloadedIcon;

        public EpisodeViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.btnEpisodeGridContainer);
            btnText = itemView.findViewById(R.id.btnEpisodeGridItem);
            lockIcon = itemView.findViewById(R.id.txtLockIcon);
            downloadedIcon = itemView.findViewById(R.id.txtDownloadedIcon);
        }
    }
}
