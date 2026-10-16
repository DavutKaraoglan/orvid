package com.orvid.yt;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonWriter;
import com.orvid.net.Diag;
import com.orvid.net.Fetch;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public final class Related {

    private static final String NEXT_URL = "https://www.youtube.com/youtubei/v1/next?prettyPrint=false";
    private static final String CLIENT_NAME = "MWEB";
    private static final String CLIENT_ID = "2";
    private static final String CLIENT_VERSION = "2.20261014.01.00";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Android 14; Mobile; rv:140.0) Gecko/140.0 Firefox/140.0";

    public static volatile boolean safeSearch;

    private Related() {
    }

    public static List<StreamInfoItem> fetch(String circuit, String videoId) throws IOException {
        return page(circuit, videoId).items;
    }

    public static Search.Page page(String circuit, String videoId) throws IOException {
        JsonObject body = new JsonObject();
        body.put("videoId", videoId);
        return toPage(post(circuit, NEXT_URL, body, "related"));
    }

    public static Search.Page more(String circuit, String continuation) throws IOException {
        JsonObject body = new JsonObject();
        body.put("continuation", continuation);
        return toPage(post(circuit, NEXT_URL, body, "related more"));
    }

    private static Search.Page toPage(String text) throws IOException {
        Object root = parse(text);
        Search.Page p = new Search.Page();
        collect(root, p.items);
        p.continuation = Search.token(root);
        return p;
    }

    static Object parse(String text) throws IOException {
        try {
            return JsonParser.any().from(text);
        } catch (Exception e) {
            throw new IOException("answer is not JSON");
        }
    }

    static String post(String circuit, String url, JsonObject body, String label) throws IOException {
        String visitor = Visionos.visitorFor(circuit);
        JsonObject client = new JsonObject();
        client.put("clientName", CLIENT_NAME);
        client.put("clientVersion", CLIENT_VERSION);
        client.put("hl", "en");
        client.put("gl", "US");
        if (visitor != null) client.put("visitorData", visitor);
        JsonObject context = new JsonObject();
        context.put("client", client);
        if (safeSearch) {
            JsonObject user = new JsonObject();
            user.put("enableSafetyMode", true);
            context.put("user", user);
        }
        body.put("context", context);
        byte[] raw = JsonWriter.string(body).getBytes(StandardCharsets.UTF_8);

        long began = System.currentTimeMillis();
        String text;
        try {
            text = Fetch.on(circuit, () -> {
                HttpURLConnection c = Fetch.open(url);
                c.setRequestMethod("POST");
                c.setRequestProperty("User-Agent", USER_AGENT);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("Accept-Encoding", "gzip");
                c.setRequestProperty("X-YouTube-Client-Name", CLIENT_ID);
                c.setRequestProperty("X-YouTube-Client-Version", CLIENT_VERSION);
                if (visitor != null) c.setRequestProperty("X-Goog-Visitor-Id", visitor);
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(raw.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(raw);
                }
                int code = c.getResponseCode();
                if (code >= 400) throw new IOException("HTTP " + code);
                InputStream in = c.getInputStream();
                if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                try (InputStream s = in) {
                    byte[] chunk = new byte[16384];
                    for (int n = s.read(chunk); n > 0; n = s.read(chunk)) buf.write(chunk, 0, n);
                }
                return new String(buf.toByteArray(), StandardCharsets.UTF_8);
            });
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
        Diag.mark(label + " " + text.length() / 1024 + "KB " + (System.currentTimeMillis() - began) + "ms");
        return text;
    }

    static void collect(Object node, List<StreamInfoItem> out) {
        if (node instanceof JsonObject) {
            JsonObject o = (JsonObject) node;
            JsonObject v = o.getObject("videoWithContextRenderer", null);
            if (v != null) {
                StreamInfoItem item = item(v);
                if (item != null) out.add(item);
                return;
            }
            for (Object child : o.values()) collect(child, out);
        } else if (node instanceof JsonArray) {
            for (Object child : (JsonArray) node) collect(child, out);
        }
    }

    private static StreamInfoItem item(JsonObject v) {
        String id = v.getString("videoId", null);
        if (id == null) return null;
        StreamInfoItem item = new StreamInfoItem(ServiceList.YouTube.getServiceId(),
                "https://www.youtube.com/watch?v=" + id, text(v.getObject("headline")), StreamType.VIDEO_STREAM);
        item.setUploaderName(text(v.getObject("shortBylineText")));
        item.setDuration(seconds(text(v.getObject("lengthText"))));
        String views = text(v.getObject("shortViewCountText"));
        item.setViewCount(com.orvid.search.Counts.views(views, "en"));
        String when = text(v.getObject("publishedTimeText"));
        item.setTextualUploadDate(when);
        long age = com.orvid.search.Counts.ageSeconds(when);
        if (age >= 0) {
            item.setUploadDate(new org.schabi.newpipe.extractor.localization.DateWrapper(
                    java.time.Instant.now().minusSeconds(age), true));
        }
        List<Image> thumbs = new ArrayList<>();
        JsonArray list = v.getObject("thumbnail").getArray("thumbnails");
        for (int i = 0; i < list.size(); i++) {
            JsonObject t = list.getObject(i);
            String url = t.getString("url", null);
            if (url == null) continue;
            if (url.startsWith("//")) url = "https:" + url;
            int h = t.getInt("height", Image.HEIGHT_UNKNOWN);
            thumbs.add(new Image(url, h, t.getInt("width", Image.WIDTH_UNKNOWN), Image.ResolutionLevel.fromHeight(h)));
        }
        item.setThumbnails(thumbs);
        return item;
    }

    private static String text(JsonObject o) {
        if (o == null) return "";
        String simple = o.getString("simpleText", null);
        if (simple != null) return simple;
        StringBuilder sb = new StringBuilder();
        JsonArray runs = o.getArray("runs");
        for (int i = 0; i < runs.size(); i++) sb.append(runs.getObject(i).getString("text", ""));
        return sb.toString();
    }

    private static long seconds(String clock) {
        if (clock.isEmpty()) return -1;
        long total = 0;
        for (String part : clock.split(":")) {
            try {
                total = total * 60 + Long.parseLong(part.trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return total;
    }
}
