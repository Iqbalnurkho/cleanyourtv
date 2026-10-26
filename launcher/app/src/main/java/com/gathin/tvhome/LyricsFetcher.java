package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Fetches synced lyrics from LRCLIB (no API key) and caches them on disk, misses for a week. */
final class LyricsFetcher {
    interface Callback { void onResult(String syncedLrc); } // null when nothing was found

    private static final String BASE = "https://lrclib.net/api/";
    private static final long MISS_RETRY_MS = 7L * 24 * 3600 * 1000;
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void fetch(Context ctx, String title, String artist, long durationMs, Callback cb) {
        Context app = ctx.getApplicationContext();
        POOL.execute(() -> {
            String result = null;
            try {
                result = load(app, title, artist, durationMs);
            } catch (Exception ignored) {
                // offline or server error: show no lyrics, try again next time the track starts
            }
            final String r = result;
            MAIN.post(() -> cb.onResult(r));
        });
    }

    private static String load(Context ctx, String title, String artist, long durationMs) throws Exception {
        File dir = new File(ctx.getCacheDir(), "lyrics");
        dir.mkdirs();
        File f = new File(dir, md5(artist.toLowerCase() + "|" + title.toLowerCase()));
        if (f.exists()) {
            String cached = readFile(f);
            if (!cached.isEmpty()) return cached;
            if (System.currentTimeMillis() - f.lastModified() < MISS_RETRY_MS) return null;
        }
        String lrc = query(title, artist, durationMs);
        writeFile(f, lrc == null ? "" : lrc);
        return lrc;
    }

    private static String query(String title, String artist, long durationMs) throws Exception {
        String q = "track_name=" + enc(title) + "&artist_name=" + enc(artist);
        String body = get(BASE + "get?" + q + (durationMs > 0 ? "&duration=" + (durationMs / 1000) : ""));
        if (body != null) {
            String s = synced(new JSONObject(body));
            if (s != null) return s;
        }
        body = get(BASE + "search?" + q);
        if (body != null) {
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                String s = synced(arr.getJSONObject(i));
                if (s != null) return s;
            }
        }
        return null;
    }

    private static String synced(JSONObject o) {
        if (o == null || o.isNull("syncedLyrics")) return null;
        String s = o.optString("syncedLyrics", "");
        return s.trim().isEmpty() ? null : s;
    }

    /** Body of a 200 response, null for 404, IOException for anything else. */
    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "cleanyourtv-music-panel (github.com/Iqbalnurkho/cleanyourtv)");
        try {
            int code = c.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new IOException("HTTP " + code);
            return readStream(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String readStream(InputStream in) throws IOException {
        try (InputStream is = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0 && out.size() < 262144) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static String readFile(File f) throws IOException {
        return readStream(new FileInputStream(f));
    }

    private static void writeFile(File f, String s) throws IOException {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String md5(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
