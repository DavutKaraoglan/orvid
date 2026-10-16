package com.orvid.dm;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.orvid.net.Diag;
import com.orvid.net.Fetch;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public final class Dailymotion {

    public static final int SERVICE_ID = 100;
    private static final String FIELDS = "id,title,owner.screenname,duration,views_total,thumbnail_480_url,created_time,aspect_ratio";
    private static final long MIN_VIEWS = 300;
    private static final Pattern VIDEO_ID = Pattern.compile("dailymotion\\.com/(?:video|embed/video)/([a-zA-Z0-9]+)|dai\\.ly/([a-zA-Z0-9]+)");
    private static final SecureRandom RANDOM = new SecureRandom();

    private Dailymotion() {
    }

    public static final class Video {
        public String id;
        public String title;
        public String author;
        public String description;
        public long views = -1;
        public String hls;
    }

    public static final class Page {
        public final List<StreamInfoItem> items = new ArrayList<>();
        public boolean hasMore;
    }

    public static String videoId(String url) {
        Matcher m = VIDEO_ID.matcher(url);
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    public static Page search(String circuit, String query, int page, String params) throws IOException {
        return search(circuit, query, page, params, false);
    }

    public static Page search(String circuit, String query, int page, String params, boolean shorts) throws IOException {
        String url = "https://api.dailymotion.com/videos?fields=" + FIELDS + "&limit=40&page=" + page
                + (params == null ? "" : params) + "&search=" + URLEncoder.encode(query, "UTF-8");
        JsonObject root = object(get(circuit, url, null, "dm search"));
        Page p = new Page();
        p.hasMore = root.getBoolean("has_more", false);
        JsonArray list = root.getArray("list");
        for (int i = 0; i < list.size(); i++) {
            JsonObject v = list.getObject(i);
            String id = v.getString("id", null);
            if (id == null) continue;
            if (v.getLong("views_total", 0) < MIN_VIEWS) continue;
            if (!shorts && v.getDouble("aspect_ratio", 1.0) < 1.0) continue;
            if (!shorts && v.getLong("duration", 0) < 60) continue;
            StreamInfoItem item = new StreamInfoItem(SERVICE_ID, "https://www.dailymotion.com/video/" + id,
                    v.getString("title", "").trim(), StreamType.VIDEO_STREAM);
            item.setUploaderName(v.getString("owner.screenname", ""));
            item.setDuration(v.getLong("duration", -1));
            item.setViewCount(v.getLong("views_total", -1));
            long created = v.getLong("created_time", 0);
            if (created > 0) {
                item.setUploadDate(new org.schabi.newpipe.extractor.localization.DateWrapper(
                        java.time.Instant.ofEpochSecond(created), false));
            }
            String thumb = v.getString("thumbnail_480_url", null);
            List<Image> thumbs = new ArrayList<>();
            if (thumb != null) thumbs.add(new Image(thumb, 270, 480, Image.ResolutionLevel.MEDIUM));
            item.setThumbnails(thumbs);
            p.items.add(item);
        }
        return p;
    }

    public static Video resolve(String circuit, String id) throws IOException {
        JsonObject root = object(get(circuit, "https://www.dailymotion.com/player/metadata/video/" + id
                + "?embedder=https%3A%2F%2Fwww.dailymotion.com%2F&locale=en", null, "dm metadata"));
        JsonObject error = root.getObject("error", null);
        if (error != null) throw new IOException(error.getString("message", "unavailable"));
        Video v = new Video();
        v.id = id;
        v.title = root.getString("title", "");
        v.author = root.getObject("owner").getString("screenname", "");
        v.description = root.getString("description", "").replaceAll("<br ?/?>", "\n").replaceAll("<[^>]+>", "");
        JsonArray auto = root.getObject("qualities").getArray("auto");
        for (int i = 0; i < auto.size(); i++) {
            JsonObject q = auto.getObject(i);
            if (q.getString("type", "").contains("mpegURL")) {
                v.hls = q.getString("url", null);
                break;
            }
        }
        if (v.hls == null) throw new IOException("no stream");
        return v;
    }

    public static Map<String, String> unblockHeaders() {
        Map<String, String> h = new TreeMap<>();
        int n = 2 + RANDOM.nextInt(7);
        for (int i = 0; i < n; i++) h.put(letters(8, 24), letters(16, 32));
        return h;
    }

    public static boolean needsUnblock(String url) {
        return url.startsWith("https://cdndirector.dailymotion.com/");
    }

    private static String letters(int min, int max) {
        String pool = "bcdfghjklmnpqrstvwxz";
        int n = min + RANDOM.nextInt(max - min + 1);
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(pool.charAt(RANDOM.nextInt(pool.length())));
        return sb.toString();
    }

    private static JsonObject object(String text) throws IOException {
        try {
            return JsonParser.object().from(text);
        } catch (Exception e) {
            throw new IOException("answer is not JSON");
        }
    }

    private static String get(String circuit, String url, Map<String, String> headers, String label) throws IOException {
        long began = System.currentTimeMillis();
        try {
            String text = Fetch.on(circuit, () -> {
                HttpURLConnection c = Fetch.open(url);
                c.setRequestProperty("User-Agent", Fetch.USER_AGENT);
                c.setRequestProperty("Accept-Encoding", "gzip");
                if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
                int code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                if (in != null) {
                    if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
                    try (InputStream s = in) {
                        byte[] chunk = new byte[16384];
                        for (int n = s.read(chunk); n > 0; n = s.read(chunk)) buf.write(chunk, 0, n);
                    }
                }
                if (code >= 400) throw new IOException("HTTP " + code);
                return new String(buf.toByteArray(), StandardCharsets.UTF_8);
            });
            Diag.mark(label + " " + text.length() / 1024 + "KB " + (System.currentTimeMillis() - began) + "ms");
            return text;
        } catch (IOException e) {
            Diag.mark(label + " FAIL " + e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
