package com.orvid.vk;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.orvid.net.Diag;
import com.orvid.net.Fetch;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Vk {

    public static final int SERVICE_ID = 102;
    private static final String APP_ID = "52461373";
    private static final String API = "https://api.vkvideo.ru/method/";
    private static final String VERSION = "5.245";
    public static final String USER_AGENT =
            "Mozilla/5.0 (Android 14; Mobile; rv:140.0) Gecko/140.0 Firefox/140.0";
    private static final Pattern VIDEO_ID = Pattern.compile("vk(?:video)?\\.(?:ru|com)/(?:video|clip)(-?\\d+_\\d+)");
    private static final long TOKEN_LIFE_MS = 20L * 60 * 60 * 1000;

    private static final Map<String, String[]> TOKENS = new HashMap<>();
    private static final Map<String, Video> KNOWN = new LinkedHashMap<String, Video>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Video> eldest) {
            return size() > 400;
        }
    };

    private Vk() {
    }

    public static final class Video {
        public String id;
        public String title;
        public String author;
        public String description;
        public long views = -1;
        public long duration;
        public String circuit;
        public final Map<String, String> files = new LinkedHashMap<>();
    }

    public static final class Page {
        public final List<StreamInfoItem> items = new ArrayList<>();
        public String next;
    }

    private static volatile String good;

    public static java.util.List<String> circuits(java.util.function.Supplier<String> fresh) {
        java.util.List<String> out = new ArrayList<>();
        String g = good;
        if (g != null) out.add(g);
        while (out.size() < 3) out.add(fresh.get());
        return out;
    }

    public static void warm(String circuit) throws IOException {
        token(circuit);
    }

    public static String videoId(String url) {
        Matcher m = VIDEO_ID.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public static Video known(String id) {
        synchronized (KNOWN) {
            return KNOWN.get(id);
        }
    }

    public static Page search(String circuit, String query, String next) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        JsonObject r;
        if (next == null) {
            params.put("q", query);
            r = call(circuit, "catalog.getVideoSearch", params, "vk search");
        } else {
            String[] parts = next.split("\n", 2);
            params.put("section_id", parts[0]);
            params.put("start_from", parts[1]);
            r = call(circuit, "catalog.getSection", params, "vk search more");
        }
        Page p = new Page();
        Map<Long, String> owners = owners(r);
        JsonArray videos = r.getArray("catalog_videos");
        for (int i = 0; i < videos.size(); i++) {
            JsonObject wrap = videos.getObject(i);
            JsonObject v = wrap.has("video") ? wrap.getObject("video") : wrap;
            Video video = parse(v, owners, circuit);
            if (video == null) continue;
            remember(video);
            p.items.add(item(v, video));
        }
        JsonObject section = r.has("section") ? r.getObject("section") : null;
        if (section == null) {
            JsonArray sections = r.getObject("catalog").getArray("sections");
            if (!sections.isEmpty()) section = sections.getObject(0);
        }
        if (section != null) {
            String from = section.getString("next_from", null);
            String id = section.getString("id", null);
            if (from != null && id != null) p.next = id + "\n" + from;
        }
        return p;
    }

    public static Video fetch(String circuit, String id) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("videos", id);
        params.put("extended", "1");
        JsonObject r = call(circuit, "video.get", params, "vk video");
        JsonArray items = r.getArray("items");
        if (items.isEmpty()) throw new IOException("video unavailable");
        Video video = parse(items.getObject(0), owners(r), circuit);
        if (video == null || video.files.isEmpty()) throw new IOException("no stream");
        remember(video);
        return video;
    }

    private static void remember(Video v) {
        synchronized (KNOWN) {
            KNOWN.put(v.id, v);
        }
    }

    private static Map<Long, String> owners(JsonObject r) {
        Map<Long, String> out = new HashMap<>();
        JsonArray groups = r.getArray("groups");
        for (int i = 0; i < groups.size(); i++) {
            JsonObject g = groups.getObject(i);
            out.put(-g.getLong("id", 0), g.getString("name", ""));
        }
        JsonArray profiles = r.getArray("profiles");
        for (int i = 0; i < profiles.size(); i++) {
            JsonObject u = profiles.getObject(i);
            out.put(u.getLong("id", 0), (u.getString("first_name", "") + " " + u.getString("last_name", "")).trim());
        }
        return out;
    }

    private static Video parse(JsonObject v, Map<Long, String> owners, String circuit) {
        long owner = v.getLong("owner_id", 0);
        long vid = v.getLong("id", 0);
        if (owner == 0 || vid == 0) return null;
        Video out = new Video();
        out.id = owner + "_" + vid;
        out.title = v.getString("title", "").trim();
        out.description = v.getString("description", "");
        out.duration = v.getLong("duration", -1);
        out.views = v.getLong("views", -1);
        out.author = owners.getOrDefault(owner, "");
        out.circuit = circuit;
        JsonObject files = v.getObject("files");
        for (String key : files.keySet()) {
            Object value = files.get(key);
            if (value instanceof String && ((String) value).startsWith("https://")) out.files.put(key, (String) value);
        }
        return out;
    }

    private static StreamInfoItem item(JsonObject v, Video video) {
        StreamInfoItem item = new StreamInfoItem(SERVICE_ID, "https://vkvideo.ru/video" + video.id,
                video.title, StreamType.VIDEO_STREAM);
        item.setUploaderName(video.author);
        item.setDuration(video.duration);
        item.setViewCount(video.views);
        long date = v.getLong("date", 0);
        if (date > 0) {
            item.setUploadDate(new org.schabi.newpipe.extractor.localization.DateWrapper(
                    Instant.ofEpochSecond(date), false));
        }
        List<Image> thumbs = new ArrayList<>();
        JsonArray images = v.getArray("image");
        for (int i = 0; i < images.size(); i++) {
            JsonObject im = images.getObject(i);
            String url = im.getString("url", null);
            if (url == null) continue;
            int h = im.getInt("height", Image.HEIGHT_UNKNOWN);
            thumbs.add(new Image(url, h, im.getInt("width", Image.WIDTH_UNKNOWN), Image.ResolutionLevel.fromHeight(h)));
        }
        item.setThumbnails(thumbs);
        return item;
    }

    private static String token(String circuit) throws IOException {
        synchronized (TOKENS) {
            String[] t = TOKENS.get(circuit);
            if (t != null && System.currentTimeMillis() < Long.parseLong(t[1])) return t[0];
        }
        String text = post(circuit, "https://login.vk.ru/?act=get_anonym_token", "client_id=" + APP_ID, "vk token");
        try {
            String token = JsonParser.object().from(text).getObject("data").getString("access_token", null);
            if (token == null) throw new IOException("no vk token");
            synchronized (TOKENS) {
                TOKENS.put(circuit, new String[] {token, Long.toString(System.currentTimeMillis() + TOKEN_LIFE_MS)});
            }
            return token;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("vk token answer is not JSON");
        }
    }

    private static JsonObject call(String circuit, String method, Map<String, String> params, String label)
            throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            body.append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8")).append('&');
        }
        body.append("v=").append(VERSION).append("&access_token=").append(token(circuit));
        String text = post(circuit, API + method, body.toString(), label);
        JsonObject root;
        try {
            root = JsonParser.object().from(text);
        } catch (Exception e) {
            throw new IOException("vk answer is not JSON");
        }
        if (root.has("error")) {
            JsonObject err = root.getObject("error");
            if (err.getInt("error_code", 0) == 5) {
                synchronized (TOKENS) {
                    TOKENS.remove(circuit);
                }
            }
            throw new IOException("vk error " + err.getInt("error_code", 0) + ": " + err.getString("error_msg", ""));
        }
        good = circuit;
        return root.getObject("response");
    }

    private static String post(String circuit, String url, String form, String label) throws IOException {
        long began = System.currentTimeMillis();
        try {
            String text = Fetch.on(circuit, () -> {
                HttpURLConnection c = Fetch.open(url);
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                byte[] raw = form.getBytes(StandardCharsets.UTF_8);
                c.setRequestMethod("POST");
                c.setRequestProperty("User-Agent", USER_AGENT);
                c.setRequestProperty("Origin", "https://m.vkvideo.ru");
                c.setRequestProperty("Referer", "https://m.vkvideo.ru/");
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                c.setRequestProperty("Accept-Encoding", "gzip");
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(raw.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(raw);
                }
                int code = c.getResponseCode();
                String body = Fetch.readBody(c);
                if (code >= 300) throw new IOException("Captcha needed (HTTP " + code + ")");
                return body;
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
