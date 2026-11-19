package com.novelex.dramas;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

final class HomeHighlightAdapter extends RecyclerView.Adapter<HomeHighlightAdapter.Holder> {
    interface Listener { void onOpen(Drama drama); }
    private final List<Drama> items;
    private final Listener listener;
    HomeHighlightAdapter(List<Drama> items, Listener listener) { this.items = items; this.listener = listener; }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_highlight, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Drama d = items.get(position);
        h.title.setText(d.getTitle());
        h.tag.setText(position == 0 ? "DESTAQUE DE HOJE" : "ESCOLHIDO PARA VOCÊ");
        String meta = d.getGenre();
        if (d.getEpisodesCount() > 0) meta = meta + "  •  " + d.getEpisodesCount() + " episódios";
        h.meta.setText(meta);
        Glide.with(h.image).load(d.getBanner()).centerCrop().into(h.image);
        h.itemView.setOnClickListener(v -> listener.onOpen(d));
    }
    @Override public int getItemCount() { return items.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        ImageView image; TextView title, tag, meta;
        Holder(View v) { super(v); image=v.findViewById(R.id.highlightImage); title=v.findViewById(R.id.highlightTitle); tag=v.findViewById(R.id.highlightTag); meta=v.findViewById(R.id.highlightMeta); }
    }
}
