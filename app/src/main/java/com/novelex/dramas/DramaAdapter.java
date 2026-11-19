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
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import java.util.List;

public class DramaAdapter extends RecyclerView.Adapter<DramaAdapter.DramaViewHolder> {

    public interface OnDramaClickListener {
        void onDramaClick(Drama drama);
    }

    private final Context context;
    private final List<Drama> dramaList;
    private final OnDramaClickListener listener;

    public DramaAdapter(Context context, List<Drama> dramaList, OnDramaClickListener listener) {
        this.context = context;
        this.dramaList = dramaList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DramaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_drama_card, parent, false);
        return new DramaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DramaViewHolder holder, int position) {
        Drama drama = dramaList.get(position);
        holder.titleText.setText(drama.getTitle());
        holder.genreText.setText(drama.getGenre());
        holder.episodesText.setText(drama.getEpisodesCount() + " EPS");
        holder.audioText.setText(drama.getAudio().equalsIgnoreCase("dublado") ? "DUB" : "LEG");

        if (drama.getPoster() != null && !drama.getPoster().isEmpty()) {
            Glide.with(context)
                .load(drama.getPoster())
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.posterImage);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDramaClick(drama);
            }
        });
    }

    @Override
    public int getItemCount() {
        return dramaList != null ? dramaList.size() : 0;
    }

    static class DramaViewHolder extends RecyclerView.ViewHolder {
        ImageView posterImage;
        TextView titleText;
        TextView genreText;
        TextView episodesText;
        TextView audioText;

        public DramaViewHolder(@NonNull View itemView) {
            super(itemView);
            posterImage = itemView.findViewById(R.id.posterImageView);
            titleText = itemView.findViewById(R.id.dramaTitle);
            genreText = itemView.findViewById(R.id.dramaGenre);
            episodesText = itemView.findViewById(R.id.badgeEpisodes);
            audioText = itemView.findViewById(R.id.badgeAudio);
        }
    }
}