package com.novelex.dramas;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class HomeReleaseAdapter extends RecyclerView.Adapter<HomeReleaseAdapter.ViewHolder> {
    public interface OnDramaClickListener {
        void onDramaClick(Drama drama);
    }

    private final Context context;
    private final List<Drama> items;
    private final OnDramaClickListener listener;

    public HomeReleaseAdapter(Context context, List<Drama> items, OnDramaClickListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_release, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Drama drama = items.get(position);
        holder.title.setText(drama.getTitle());

        if (drama.getYear() > 0) {
            holder.badge.setText(String.valueOf(drama.getYear()));
        } else {
            holder.badge.setText("NOVO");
        }

        StringBuilder sub = new StringBuilder();
        if (drama.getEpisodesCount() > 0) {
            sub.append(drama.getEpisodesCount()).append(" EPS");
        }
        if (drama.getGenre() != null && !drama.getGenre().isEmpty()) {
            if (sub.length() > 0) sub.append(" • ");
            sub.append(drama.getGenre());
        }
        holder.subtitle.setText(sub.length() > 0 ? sub.toString() : "Lançamento");

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
        final TextView title;
        final TextView subtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.releasePoster);
            badge = itemView.findViewById(R.id.releaseBadge);
            title = itemView.findViewById(R.id.releaseTitle);
            subtitle = itemView.findViewById(R.id.releaseSubtitle);
        }
    }
}
