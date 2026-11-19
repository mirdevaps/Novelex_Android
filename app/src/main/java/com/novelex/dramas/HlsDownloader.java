package com.novelex.dramas;

import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class HlsDownloader {

    private static final String TAG = "NovelexHlsDownloader";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    public interface DownloadCallback {
        void onProgress(int progressPercent);
        void onSuccess(File destinationFile);
        void onError(String error);
    }

    private static class ResolvedPlaylist {
        String finalUrl;
        String content;
        String baseUrl;

        ResolvedPlaylist(String finalUrl, String content, String baseUrl) {
            this.finalUrl = finalUrl;
            this.content = content;
            this.baseUrl = baseUrl;
        }
    }

    // Resolve todos os redirecionamentos (301, 302, 307, 308) cross-domain
    private static ResolvedPlaylist fetchPlaylistWithRedirects(String initialUrl) throws Exception {
        String currentUrl = initialUrl;
        int redirects = 0;

        while (redirects < 10) {
            URL url = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.connect();

            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                if (loc != null) {
                    if (loc.startsWith("/")) {
                        URL prev = new URL(currentUrl);
                        currentUrl = prev.getProtocol() + "://" + prev.getHost() + loc;
                    } else {
                        currentUrl = loc;
                    }
                    conn.disconnect();
                    redirects++;
                    continue;
                }
            }

            if (code >= 200 && code < 300) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                conn.disconnect();

                String finalUrl = currentUrl;
                String baseUrl = finalUrl.substring(0, finalUrl.lastIndexOf('/') + 1);
                return new ResolvedPlaylist(finalUrl, sb.toString(), baseUrl);
            } else {
                conn.disconnect();
                throw new Exception("Falha HTTP " + code + " ao obter playlist de " + currentUrl);
            }
        }
        throw new Exception("Muitos redirecionamentos em " + initialUrl);
    }

    // Abre stream de segmento resolvendo redirecionamentos
    private static InputStream openSegmentStream(String urlStr) throws Exception {
        String currentUrl = urlStr;
        int redirects = 0;

        while (redirects < 10) {
            URL url = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.connect();

            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                if (loc != null) {
                    if (loc.startsWith("/")) {
                        URL prev = new URL(currentUrl);
                        currentUrl = prev.getProtocol() + "://" + prev.getHost() + loc;
                    } else {
                        currentUrl = loc;
                    }
                    conn.disconnect();
                    redirects++;
                    continue;
                }
            }

            if (code >= 200 && code < 300) {
                return conn.getInputStream();
            } else {
                conn.disconnect();
                throw new Exception("HTTP " + code + " ao baixar segmento: " + currentUrl);
            }
        }
        throw new Exception("Muitos redirecionamentos no segmento " + urlStr);
    }

    public static void downloadHlsAsSingleVideo(String m3u8Url, File destinationFile, DownloadCallback callback) {
        new Thread(() -> {
            FileOutputStream fos = null;
            try {
                Log.d(TAG, "Iniciando download HLS: " + m3u8Url);

                // 1. Obtém a playlist resolvendo redirects cross-domain
                ResolvedPlaylist pl = fetchPlaylistWithRedirects(m3u8Url);
                String playlistText = pl.content;
                String baseUrl = pl.baseUrl;

                // 2. Se for uma master playlist (contendo variantes .m3u8), pega a primeira variante de mídia
                String[] lines = playlistText.split("\n");
                String variantM3u8Url = null;
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        if (line.contains(".m3u8")) {
                            variantM3u8Url = line.startsWith("http") ? line : baseUrl + line;
                            break;
                        }
                    }
                }

                if (variantM3u8Url != null) {
                    Log.d(TAG, "Master playlist detectada. Baixando playlist de variante: " + variantM3u8Url);
                    pl = fetchPlaylistWithRedirects(variantM3u8Url);
                    playlistText = pl.content;
                    baseUrl = pl.baseUrl;
                    lines = playlistText.split("\n");
                }

                // 3. Extrai as URLs completas de todos os segmentos .ts
                List<String> segmentUrls = new ArrayList<>();
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        String fullSegUrl = line.startsWith("http") ? line : baseUrl + line;
                        segmentUrls.add(fullSegUrl);
                    }
                }

                if (segmentUrls.isEmpty()) {
                    if (callback != null) callback.onError("Nenhum segmento encontrado na playlist HLS.");
                    return;
                }

                Log.d(TAG, "Encontrados " + segmentUrls.size() + " segmentos para download.");

                // 4. Cria arquivo temporário para montagem
                File tempFile = new File(destinationFile.getAbsolutePath() + ".tmp");
                if (tempFile.exists()) tempFile.delete();
                if (tempFile.getParentFile() != null) tempFile.getParentFile().mkdirs();

                fos = new FileOutputStream(tempFile);
                byte[] buffer = new byte[32768];
                int totalSegments = segmentUrls.size();

                for (int i = 0; i < totalSegments; i++) {
                    String segUrl = segmentUrls.get(i);
                    InputStream segIs = openSegmentStream(segUrl);
                    int read;
                    while ((read = segIs.read(buffer)) > 0) {
                        fos.write(buffer, 0, read);
                    }
                    segIs.close();

                    int progress = (int) (((i + 1) / (float) totalSegments) * 100);
                    if (callback != null) {
                        callback.onProgress(progress);
                    }
                }

                fos.flush();
                fos.close();
                fos = null;

                // Move temporário para destino final
                if (destinationFile.exists()) destinationFile.delete();
                tempFile.renameTo(destinationFile);

                long sizeMb = destinationFile.length() / (1024 * 1024);
                Log.d(TAG, "Download offline concluído com sucesso: " + destinationFile.getAbsolutePath() + " (" + sizeMb + " MB)");

                if (callback != null) {
                    callback.onSuccess(destinationFile);
                }

            } catch (Exception e) {
                Log.e(TAG, "Erro no download: " + e.getMessage(), e);
                if (fos != null) {
                    try { fos.close(); } catch (Exception ignored) {}
                }
                if (destinationFile.exists() && destinationFile.length() == 0) {
                    destinationFile.delete();
                }
                if (callback != null) {
                    callback.onError(e.getMessage() != null ? e.getMessage() : "Erro desconhecido");
                }
            }
        }).start();
    }
}
