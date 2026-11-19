package com.novelex.dramas;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class HomeContinueAdapter extends RecyclerView.Adapter<HomeContinueAdapter.ViewHolder> {
    public interface OnDramaClickListener {
        void onDramaClick(Drama drama);
    }

    private final Context context;
    private final List<Drama> items;
    private final SharedPreferences prefs;
    private final OnDramaClickListener listener;

    public HomeContinueAdapter(Context context, List<Drama> items, SharedPreferences prefs, OnDramaClickListener listener) {
        this.context = context;
        this.items = items;
        this.prefs = prefs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_continue, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Drama drama = items.get(position);
        holder.title.setText(drama.getTitle());

        int lastEpIndex = prefs.getInt("last_episode_index_" + drama.getId(), 0);
        int currentEpNumber = lastEpIndex + 1;
        int totalEpisodes = drama.getEpisodesCount();

        holder.badge.setText("EP " + currentEpNumber);

        if (totalEpisodes > 0) {
            holder.subtitle.setText("Continuar EP " + currentEpNumber + " de " + totalEpisodes);
            int progress = Math.min(100, Math.max(12, Math.round(((float) currentEpNumber / totalEpisodes) * 100f)));
            holder.progress.setProgress(progress);
        } else {
            holder.subtitle.setText("Continuar EP " + currentEpNumber);
            holder.progress.setProgress(30);
        }

        String posterUrl = drama.getPoster();
        if (posterUrl == null || posterUrl.isEmpty()) {
            posterUrl = drama.getBanner();
        }

        Glide.with(holder.poster.getContext())
            .load(posterUrl)
            .centerCrop()
            .into(holder.poster);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDramaClick(drama);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView poster;
        final TextView badge;
        final ProgressBar progress;
        final TextView title;
        final TextView subtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.continuePoster);
            badge = itemView.findViewById(R.id.continueEpisodeBadge);
            progress = itemView.findViewById(R.id.continueProgress);
            title = itemView.findViewById(R.id.continueTitle);
            subtitle = itemView.findViewById(R.id.continueSubtitle);
        }
    }
}
