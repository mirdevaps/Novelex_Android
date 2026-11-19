package com.novelex.dramas;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "NovelexDB";
    private static final String DB_NAME = "novelex.db";
    private static final int DB_VERSION = 5;
    private final Context context;
    private final String dbPath;

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
        this.dbPath = context.getDatabasePath(DB_NAME).getAbsolutePath();
        copyDatabaseIfNeeded();
    }

    private void copyDatabaseIfNeeded() {
        File dbFile = new File(dbPath);
        if (dbFile.exists()) {
            SQLiteDatabase installed = null;
            try {
                installed = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);
                if (installed.getVersion() < DB_VERSION) {
                    installed.close();
                    installed = null;
                    if (!dbFile.delete()) {
                        Log.e(TAG, "Não foi possível atualizar o catálogo local.");
                        return;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Não foi possível conferir a versão do catálogo.", e);
                return;
            } finally {
                if (installed != null) installed.close();
            }
        }
        if (!dbFile.exists()) {
            File parent = dbFile.getParentFile();
            if (parent != null) parent.mkdirs();
            try (InputStream is = new java.io.BufferedInputStream(context.getAssets().open(DB_NAME));
                 OutputStream os = new java.io.BufferedOutputStream(new FileOutputStream(dbFile))) {
                byte[] buffer = new byte[65536];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
                os.flush();
                Log.d(TAG, "Banco SQLite copiado dos assets com sucesso!");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao copiar novelex.db: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.enableWriteAheadLogging();
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        for (String[] column : new String[][]{{"dramas", "year", "INTEGER DEFAULT 0"}, {"episodes", "thumbnail", "TEXT"}}) {
            boolean exists = false;
            try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + column[0] + ")", null)) {
                while (cursor.moveToNext()) if (column[1].equals(cursor.getString(1))) exists = true;
            }
            if (!exists) db.execSQL("ALTER TABLE " + column[0] + " ADD COLUMN " + column[1] + " " + column[2]);
        }
        ensureFrutasDrama(db);
    }

    public synchronized void ensureFrutasDramaSync() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ensureFrutasDrama(db);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao sincronizar Novela das Frutas via ensureFrutasDramaSync", e);
        }
    }

    private void ensureFrutasDrama(SQLiteDatabase db) {
        if (db == null || db.isReadOnly()) return;
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM episodes WHERE drama_id = 'novela_das_frutas'", null);
            if (c != null && c.moveToFirst() && c.getInt(0) >= 58) {
                return;
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }

        try (InputStream is = context.getAssets().open("frutas_drama.json")) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            String json = baos.toString("UTF-8");
            org.json.JSONObject obj = new org.json.JSONObject(json);
            db.beginTransaction();
            try {
                db.execSQL("INSERT OR REPLACE INTO dramas (id, title, poster, banner, synopsis, genre, audio, ep_count, year) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{
                        obj.optString("id", "novela_das_frutas"),
                        obj.optString("title", "Novela das Frutas"),
                        obj.optString("poster"),
                        obj.optString("banner"),
                        obj.optString("synopsis"),
                        obj.optString("genre", "Frutas"),
                        obj.optString("audio", "Dublado"),
                        obj.optInt("ep_count", 58),
                        obj.optInt("year", 2026)
                    });
                db.execSQL("DELETE FROM episodes WHERE drama_id = 'novela_das_frutas'");
                org.json.JSONArray eps = obj.optJSONArray("episodes");
                if (eps != null) {
                    for (int i = 0; i < eps.length(); i++) {
                        org.json.JSONObject epObj = eps.getJSONObject(i);
                        db.execSQL("INSERT INTO episodes (drama_id, number, title, url) VALUES (?, ?, ?, ?)",
                            new Object[]{
                                "novela_das_frutas",
                                epObj.optInt("num", i + 1),
                                epObj.optString("title", "Episódio " + (i + 1)),
                                epObj.optString("url", epObj.optString("stream_url", ""))
                            });
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            Log.i(TAG, "Novela das Frutas sincronizada no banco com 58 episódios!");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao garantir Novela das Frutas", e);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {}

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public List<Drama> getAllDramas(int limit, int offset) {
        List<Drama> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT id, title, poster, banner, synopsis, genre, audio, ep_count, year FROM dramas LIMIT ? OFFSET ?";
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(limit), String.valueOf(offset)});

        if (cursor.moveToFirst()) {
            do {
                Drama d = new Drama();
                d.setId(cursor.getString(0));
                d.setTitle(cursor.getString(1));
                d.setPoster(cursor.getString(2));
                d.setBanner(cursor.getString(3));
                d.setSynopsis(cursor.getString(4));
                d.setGenre(cursor.getString(5));
                d.setAudio(cursor.getString(6));
                d.setEpisodesCount(cursor.getInt(7));
                    d.setYear(cursor.getInt(8));
                list.add(d);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public List<Drama> getDramasForCatalog(boolean doramas, int limit, int offset) {
        String typeFilter = doramas ? "genre LIKE '%Dorama%'" : "genre NOT LIKE '%Dorama%'";
        return queryDramas("SELECT id, title, poster, banner, synopsis, genre, audio, ep_count, year FROM dramas WHERE "
            + typeFilter + " ORDER BY title COLLATE NOCASE LIMIT ? OFFSET ?",
            new String[]{String.valueOf(limit), String.valueOf(offset)});
    }

    public List<Drama> getDramasByCategory(String category, boolean doramas) {
        if (category != null && category.matches("[0-9]{4}")) {
            return queryDramas("SELECT id, title, poster, banner, synopsis, genre, audio, ep_count, year FROM dramas WHERE "
                + (doramas ? "genre LIKE '%Dorama%'" : "genre NOT LIKE '%Dorama%'") + " AND year = ? ORDER BY title COLLATE NOCASE", new String[]{category});
        }
        if (category == null || category.equalsIgnoreCase("Todos")) {
            return getDramasForCatalog(doramas, 1200, 0);
        }
        String typeFilter = doramas ? "genre LIKE '%Dorama%'" : "genre NOT LIKE '%Dorama%'";
        String query;
        String[] args;
        if (category.equalsIgnoreCase("Dublados") || category.equalsIgnoreCase("Dublado")) {
            query = "SELECT id, title, poster, banner, synopsis, genre, audio, ep_count, year FROM dramas WHERE "
                + typeFilter + " AND (audio LIKE '%dublado%' OR audio LIKE '%DUB%') LIMIT 120";
            args = null;
        } else {
            query = "SELECT id, title, poster, banner, synopsis, genre, audio, ep_count, year FROM dramas WHERE "
                + typeFilter + " AND (genre LIKE ? OR title LIKE ?) LIMIT 120";
            String filter = "%" + category + "%";
            args = new String[]{filter, filter};
        }
        return queryDramas(query, args);
    }

    public List<Drama> searchDramas(String queryText, boolean doramas) {
        if (queryText == null || queryText.trim().isEmpty()) {
            return getDramasForCatalog(doramas, 1200, 0);
        }
        String typeFilter = doramas ? "genre LIKE '%Dorama%'" : "genre NOT LIKE '%Dorama%'";
        String filter = "%" + queryText.trim() + "%";
        return queryDramas("SELECT id, title, poster, banner, synopsis, genre, audio, ep_count, year FROM dramas WHERE "
            + typeFilter + " AND (title LIKE ? OR synopsis LIKE ? OR genre LIKE ?) ORDER BY title COLLATE NOCASE LIMIT 120",
            new String[]{filter, filter, filter});
    }

    private List<Drama> queryDramas(String query, String[] args) {
        List<Drama> list = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(query, args);
        try {
            if (cursor.moveToFirst()) {
                do {
                    Drama d = new Drama();
                    d.setId(cursor.getString(0));
                    d.setTitle(cursor.getString(1));
                    d.setPoster(cursor.getString(2));
                    d.setBanner(cursor.getString(3));
                    d.setSynopsis(cursor.getString(4));
                    d.setGenre(cursor.getString(5));
                    d.setAudio(cursor.getString(6));
                    d.setEpisodesCount(cursor.getInt(7));
                    d.setYear(cursor.getInt(8));
                    list.add(d);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    public List<Drama> getDramasByCategory(String category) {
        if (category == null || category.equalsIgnoreCase("Todos")) {
            return getAllDramas(150, 0);
        }
        
        List<Drama> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor;

        if (category.equalsIgnoreCase("Dublados") || category.equalsIgnoreCase("Dublado")) {
            String query = "SELECT id, title, poster, banner, synopsis, genre, audio, ep_count, year FROM dramas WHERE audio LIKE '%dublado%' OR audio LIKE '%DUB%'";
            cursor = db.rawQuery(query, null);
        } else {
            String query = "SELECT id, title, poster, banner, synopsis, genre, audio, ep_count, year FROM dramas WHERE genre LIKE ? OR title LIKE ? LIMIT 120";
            String filter = "%" + category + "%";
            cursor = db.rawQuery(query, new String[]{filter, filter});
        }

        if (cursor.moveToFirst()) {
            do {
                Drama d = new Drama();
                d.setId(cursor.getString(0));
                d.setTitle(cursor.getString(1));
                d.setPoster(cursor.getString(2));
                d.setBanner(cursor.getString(3));
                d.setSynopsis(cursor.getString(4));
                d.setGenre(cursor.getString(5));
                d.setAudio(cursor.getString(6));
                d.setEpisodesCount(cursor.getInt(7));
                    d.setYear(cursor.getInt(8));
                list.add(d);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public List<Drama> searchDramas(String queryText) {
        List<Drama> list = new ArrayList<>();
        if (queryText == null || queryText.trim().isEmpty()) {
            return getAllDramas(150, 0);
        }
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT id, title, poster, banner, synopsis, genre, audio, ep_count, year FROM dramas WHERE title LIKE ? OR synopsis LIKE ? OR genre LIKE ? LIMIT 120";
        String filter = "%" + queryText.trim() + "%";
        Cursor cursor = db.rawQuery(query, new String[]{filter, filter, filter});

        if (cursor.moveToFirst()) {
            do {
                Drama d = new Drama();
                d.setId(cursor.getString(0));
                d.setTitle(cursor.getString(1));
                d.setPoster(cursor.getString(2));
                d.setBanner(cursor.getString(3));
                d.setSynopsis(cursor.getString(4));
                d.setGenre(cursor.getString(5));
                d.setAudio(cursor.getString(6));
                d.setEpisodesCount(cursor.getInt(7));
                    d.setYear(cursor.getInt(8));
                list.add(d);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public List<String> getShortDramaYears() {
        List<String> years = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT DISTINCT year FROM dramas WHERE genre NOT LIKE '%Dorama%' AND year > 0 ORDER BY year DESC", null)) {
            while (cursor.moveToNext()) years.add(cursor.getString(0));
        }
        return years;
    }

    public List<Episode> getEpisodesForDrama(String dramaId) {
        List<Episode> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT number, title, url, thumbnail FROM episodes WHERE drama_id = ? ORDER BY number ASC", new String[]{dramaId});

        if (cursor.moveToFirst()) {
            do {
                Episode ep = new Episode();
                ep.setNumber(cursor.getInt(0));
                ep.setTitle(cursor.getString(1));
                ep.setStreamUrl(cursor.getString(2));
                ep.setThumbnail(cursor.getString(3));
                list.add(ep);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }
}
