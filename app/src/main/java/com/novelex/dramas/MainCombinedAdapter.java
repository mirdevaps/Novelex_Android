package com.novelex.dramas;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import java.util.List;
import java.util.Map;

public class MainCombinedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_DRAMA = 1;
    public static final int TYPE_NATIVE_AD = 2;

    public interface OnDramaClickListener {
        void onDramaClick(Drama drama);
    }

    public interface OnCategorySelectListener {
        void onCategorySelected(String category);
    }

    public interface OnNativeAdSlotRequestedListener {
        void onNativeAdSlotRequested(int slotIndex);
    }

    private final Context context;
    private final List<Drama> dramaList;
    private final List<String> categories;
    private Drama heroDrama;
    private boolean doramaCatalog;
    private String sectionTitle;
    private String sectionSubtitle;
    private final Map<Integer, NativeAd> nativeAds;
    private final OnDramaClickListener dramaListener;
    private final OnCategorySelectListener categoryListener;
    private final OnNativeAdSlotRequestedListener nativeAdSlotListener;

    private static final int FIRST_AD_AFTER_DRAMAS = 4;
    private static final int DRAMAS_BETWEEN_ADS = 24;
    private static final int MAX_NATIVE_AD_SLOTS = 6;

    public MainCombinedAdapter(Context context, List<Drama> dramaList, List<String> categories,
                               OnDramaClickListener dramaListener, OnCategorySelectListener categoryListener,
                               Map<Integer, NativeAd> nativeAds,
                               OnNativeAdSlotRequestedListener nativeAdSlotListener) {
        this.context = context;
        sectionTitle = context.getString(R.string.ui_24);
        sectionSubtitle = context.getString(R.string.ui_25);
        this.dramaList = dramaList;
        this.categories = new java.util.ArrayList<>(categories);
        this.dramaListener = dramaListener;
        this.categoryListener = categoryListener;
        this.nativeAds = nativeAds;
        this.nativeAdSlotListener = nativeAdSlotListener;
    }

    public void setHeroDrama(Drama heroDrama) {
        this.heroDrama = heroDrama;
        notifyItemChanged(0);
    }

    public void setCategories(List<String> newCategories) {
        categories.clear();
        categories.addAll(newCategories);
        notifyItemChanged(0);
    }

    public void setDoramaCatalog(boolean doramaCatalog) {
        this.doramaCatalog = doramaCatalog;
        notifyItemChanged(0);
    }

    public void setSectionContent(String title, String subtitle) {
        sectionTitle = title;
        sectionSubtitle = subtitle;
        notifyItemChanged(0);
    }

    public void notifyNativeAdChanged(int slotIndex) {
        int position = adapterPositionForAdSlot(slotIndex);
        if (position >= 0 && position < getItemCount()) notifyItemChanged(position);
    }

    public boolean isFullSpan(int position) {
        return position == 0 || nativeAdSlotAtPosition(position) >= 0;
    }

    public int positionForDrama(int index) {
        int position = index + 1;
        for (int slot = 0; slot < availableAdSlotCount(); slot++) {
            if (FIRST_AD_AFTER_DRAMAS + slot * DRAMAS_BETWEEN_ADS <= index) position++;
        }
        return position;
    }

    private int availableAdSlotCount() {
        if (nativeAdSlotListener == null) return 0;
        if (dramaList == null || dramaList.size() < FIRST_AD_AFTER_DRAMAS) return 0;
        int available = 1 + (dramaList.size() - FIRST_AD_AFTER_DRAMAS) / DRAMAS_BETWEEN_ADS;
        return Math.min(MAX_NATIVE_AD_SLOTS, available);
    }

    private int adapterPositionForAdSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= availableAdSlotCount()) return -1;
        int dramaCountBefore = FIRST_AD_AFTER_DRAMAS + slotIndex * DRAMAS_BETWEEN_ADS;
        return 1 + dramaCountBefore + slotIndex;
    }

    private int nativeAdSlotAtPosition(int position) {
        for (int slot = 0; slot < availableAdSlotCount(); slot++) {
            if (adapterPositionForAdSlot(slot) == position) return slot;
        }
        return -1;
    }

    private int dramaIndexAtPosition(int position) {
        int adsBefore = 0;
        for (int slot = 0; slot < availableAdSlotCount(); slot++) {
            if (adapterPositionForAdSlot(slot) < position) adsBefore++;
            else break;
        }
        return position - 1 - adsBefore;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return TYPE_HEADER;
        if (nativeAdSlotAtPosition(position) >= 0) return TYPE_NATIVE_AD;
        return TYPE_DRAMA;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(context).inflate(R.layout.layout_header_and_hero, parent, false);
            return new HeaderViewHolder(v);
        } else if (viewType == TYPE_NATIVE_AD) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_main_native_ad, parent, false);
            return new NativeAdViewHolder(v);
        } else {
            View v = LayoutInflater.from(context).inflate(R.layout.item_drama_card, parent, false);
            return new DramaViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder h = (HeaderViewHolder) holder;
            h.catRv.setVisibility(doramaCatalog ? View.GONE : View.VISIBLE);
            h.itemView.findViewById(R.id.sectionEyebrow).setVisibility(doramaCatalog ? View.GONE : View.VISIBLE);
            h.sectionTitle.setTypeface(android.graphics.Typeface.create(
                doramaCatalog ? "sans-serif-medium" : "serif", android.graphics.Typeface.BOLD));
            h.sectionTitle.setTextSize(doramaCatalog ? 26 : 21);
            h.sectionTitle.setPadding(0, doramaCatalog ? (int) (20 * context.getResources().getDisplayMetrics().density) : 0, 0, 0);
            if (h.catRv.getAdapter() == null) {
                h.catRv.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
                h.categoryAdapter = new CategoryAdapter(context, categories, categoryListener::onCategorySelected);
                h.catRv.setAdapter(h.categoryAdapter);
            } else if (h.categoryAdapter != null) {
                h.categoryAdapter.updateCategories(categories);
            }

            if (heroDrama != null && !doramaCatalog) {
                h.heroCard.setVisibility(View.VISIBLE);
                h.heroTitle.setText(heroDrama.getTitle());
                h.heroSubtitle.setText((doramaCatalog ? "DORAMA ASIÁTICO" : "NOVELA CURTA")
                    + " • " + heroDrama.getEpisodesCount() + " EPISÓDIOS");
                Glide.with(context)
                    .load(heroDrama.getBanner())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(h.heroImage);

                h.btnPlay.setOnClickListener(v -> dramaListener.onDramaClick(heroDrama));
                h.heroCard.setOnClickListener(v -> dramaListener.onDramaClick(heroDrama));
            } else {
                h.heroCard.setVisibility(View.GONE);
            }
            h.sectionTitle.setText(sectionTitle);
            h.sectionSubtitle.setText(sectionSubtitle);
        } else if (holder instanceof NativeAdViewHolder) {
            int slotIndex = nativeAdSlotAtPosition(position);
            NativeAd ad = nativeAds.get(slotIndex);
            NativeAdViewHolder adHolder = (NativeAdViewHolder) holder;
            adHolder.bind(ad);
            if (ad == null && nativeAdSlotListener != null) {
                nativeAdSlotListener.onNativeAdSlotRequested(slotIndex);
            }
        } else if (holder instanceof DramaViewHolder) {
            Drama drama = dramaList.get(dramaIndexAtPosition(position));
            DramaViewHolder d = (DramaViewHolder) holder;

            d.title.setText(drama.getTitle());
            d.genre.setText(drama.getGenre() + (drama.getYear() > 0 ? " · " + drama.getYear() : ""));
            d.episodes.setText(drama.getEpisodesCount() + " EPS");
            d.audio.setText(drama.getAudio().equalsIgnoreCase("dublado") ? "DUB" : drama.getAudio().equalsIgnoreCase("legendado") ? "LEG" : "—");

            Glide.with(context)
                .load(drama.getPoster())
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(300, 420)
                .into(d.poster);

            d.itemView.setOnClickListener(v -> dramaListener.onDramaClick(drama));
        }
    }

    @Override
    public int getItemCount() {
        return (dramaList != null ? dramaList.size() : 0) + 1 + availableAdSlotCount();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        RecyclerView catRv;
        CategoryAdapter categoryAdapter;
        CardView heroCard;
        ImageView heroImage;
        TextView heroTitle;
        TextView heroSubtitle;
        Button btnPlay;
        TextView sectionTitle;
        TextView sectionSubtitle;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            catRv = itemView.findViewById(R.id.categoriesRecyclerView);
            heroCard = itemView.findViewById(R.id.heroCard);
            heroImage = itemView.findViewById(R.id.heroBannerImage);
            heroTitle = itemView.findViewById(R.id.heroTitle);
            heroSubtitle = itemView.findViewById(R.id.heroSubtitle);
            btnPlay = itemView.findViewById(R.id.btnHeroPlay);
            sectionTitle = itemView.findViewById(R.id.sectionTitle);
            sectionSubtitle = itemView.findViewById(R.id.sectionSubtitle);
        }
    }

    static class DramaViewHolder extends RecyclerView.ViewHolder {
        ImageView poster;
        TextView title;
        TextView genre;
        TextView episodes;
        TextView audio;

        public DramaViewHolder(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.posterImageView);
            title = itemView.findViewById(R.id.dramaTitle);
            genre = itemView.findViewById(R.id.dramaGenre);
            episodes = itemView.findViewById(R.id.badgeEpisodes);
            audio = itemView.findViewById(R.id.badgeAudio);
        }
    }

    static class NativeAdViewHolder extends RecyclerView.ViewHolder {
        final NativeAdView adView;
        final TextView headline;
        final TextView body;
        final TextView callToAction;
        final ImageView icon;
        NativeAd boundNativeAd;

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
