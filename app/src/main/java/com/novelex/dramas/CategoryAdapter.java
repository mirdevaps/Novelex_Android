package com.novelex.dramas;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(String category);
    }

    private final Context context;
    private final List<String> categories;
    private int selectedPosition = 0;
    private final OnCategoryClickListener listener;

    public CategoryAdapter(Context context, List<String> categories, OnCategoryClickListener listener) {
        this.context = context;
        this.categories = new ArrayList<>(categories);
        this.listener = listener;
    }

    public void updateCategories(List<String> newCategories) {
        if (categories.equals(newCategories)) return;
        categories.clear();
        categories.addAll(newCategories);
        selectedPosition = 0;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category_chip, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        String category = categories.get(position);
        holder.chipText.setText(category.equals("Todos") ? context.getString(R.string.ui_42) : (category.equals("Dublados") ? context.getString(R.string.ui_43) : (category.equals("Histórias da Ásia") ? context.getString(R.string.ui_44) : (category.equals("Identidade Escondida") ? context.getString(R.string.ui_45) : (category.equals("Bilionários & CEOs") ? context.getString(R.string.ui_46) : (category.equals("Vingança") ? context.getString(R.string.ui_47) : (category.equals("Romance") ? context.getString(R.string.ui_48) : (category.equals("Lançamentos") ? context.getString(R.string.ui_49) : (category)))))))));
        holder.itemView.setContentDescription("Categoria " + category);
        holder.itemView.setSelected(position == selectedPosition);

        if (position == selectedPosition) {
            holder.chipText.setBackgroundResource(R.drawable.bg_category_minicard_selected);
            holder.chipText.setTextColor(context.getColor(R.color.novelex_primary_pink));
        } else {
            holder.chipText.setBackgroundResource(R.drawable.bg_category_minicard);
            holder.chipText.setTextColor(context.getColor(R.color.novelex_text_muted));
        }


        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            int prev = selectedPosition;
            selectedPosition = pos;
            notifyItemChanged(prev);
            notifyItemChanged(selectedPosition);
            if (listener != null) {
                listener.onCategoryClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories != null ? categories.size() : 0;
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView chipText;
        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            chipText = itemView.findViewById(R.id.chipText);
        }
    }
}
