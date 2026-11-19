package com.novelex.dramas;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import okhttp3.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class ReelsCatalogSync {
    static synchronized int sync(DatabaseHelper helper) throws Exception {
        OkHttpClient client = PlaybackNetwork.client().newBuilder().callTimeout(90, TimeUnit.SECONDS).build();
        Request request = new Request.Builder()
            .url("https://mgeb.space/api/reels/Jackers/600a094a2e9cc27e")
            .header("Accept", "application/json").header("User-Agent", "Novelex/1.1.0").build();
        JSONArray items;
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("Catalog HTTP " + response.code());
            items = new JSONArray(response.body().string());
        }
        JSONArray playable = new JSONArray();
        Set<String> unique = new HashSet<>();
        for (int i=0; i<items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            JSONArray episodes = item.optJSONArray("episodes");
            if (episodes == null || episodes.length() == 0 || item.optString("title").trim().isEmpty()
                || item.optString("id").isEmpty() || !unique.add(item.optString("id"))) continue;
            playable.put(item);
        }
        items = playable;
        if (items.length() == 0) throw new java.io.IOException("Empty catalog");
        // Validate the complete snapshot before replacing cached titles.
        Set<String> ids = new HashSet<>();
        for (int i=0; i<items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String id = item.getString("id");
            if (id.isEmpty() || !ids.add(id) || item.getString("title").trim().isEmpty()
                || item.getJSONArray("episodes").length() == 0) throw new JSONException("Invalid catalog item");
            JSONArray episodes = item.getJSONArray("episodes");
            for (int j=0; j<episodes.length(); j++) {
                JSONObject episode = episodes.getJSONObject(j);
                String url = episode.optString("url", episode.optString("direct_source"));
                if (episode.getInt("episode_number") < 1 || !(url.startsWith("https://") || url.startsWith("http://")))
                    throw new JSONException("Invalid episode");
            }
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM episodes WHERE drama_id IN (SELECT id FROM dramas WHERE genre NOT LIKE '%Dorama%')");
            db.delete("dramas", "genre NOT LIKE '%Dorama%'", null);
            for (int i=0; i<items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                JSONArray episodes = item.getJSONArray("episodes");
                String id = item.getString("id");
                ContentValues title = new ContentValues();
                title.put("year", item.optInt("year", 0));
                title.put("id", id); title.put("title", item.getString("title"));
                title.put("poster", item.optString("poster_url", item.optString("cover")));
                title.put("banner", item.optString("cover", item.optString("poster_url")));
                title.put("synopsis", item.optString("plot", item.optString("description")));
                title.put("genre", item.optString("genre", item.optString("category", "Novela curta")));
                title.put("audio", item.optString("audio_type", "Não informado"));
                title.put("ep_count", episodes.length()); db.insertOrThrow("dramas", null, title);
                for (int j=0; j<episodes.length(); j++) {
                    JSONObject episode = episodes.getJSONObject(j);
                    ContentValues row = new ContentValues(); row.put("drama_id", id);
                    row.put("thumbnail", episode.optString("still_path"));
                    row.put("number", episode.getInt("episode_number"));
                    row.put("title", episode.optString("title", episode.optString("name")));
                    row.put("url", episode.optString("url", episode.optString("direct_source")));
                    db.insertOrThrow("episodes", null, row);
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return items.length();
    }
}
