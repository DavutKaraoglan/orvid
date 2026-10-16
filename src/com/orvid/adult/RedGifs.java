package com.orvid.adult;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RedGifs {

    public static final int SERVICE_ID = 112;
    private static final String API = "https://api.redgifs.com/v2/";
    private static final Pattern VIDEO = Pattern.compile("redgifs\\.com/(?:watch|ifr)/([A-Za-z]+)");
    private static final long TOKEN_LIFE_MS = 20L * 60 * 60 * 1000;
    private static final Map<String, String[]> TOKENS = new HashMap<>();

    private RedGifs() {
    }

    public static final class Page {
        public final List<StreamInfoItem> items = new ArrayList<>();
        public String next;
    }

    public static final class Media {
        public String title = "";
        public String author = "";
        public String mp4;
    }

    public static boolean handles(String url) {
        return url != null && VIDEO.matcher(url).find();
    }

    public static Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 1 : Integer.parseInt(next);
        JsonObject root = call(circuit, "gifs/search?search_text=" + URLEncoder.encode(query, "UTF-8")
                + "&count=40&order=top&page=" + page, "redgifs search");
        Page p = new Page();
        JsonArray gifs = root.getArray("gifs");
        for (int i = 0; i < gifs.size(); i++) {
            JsonObject g = gifs.getObject(i);
            String id = g.getString("id", null);
            if (id == null) continue;
            StreamInfoItem item = new StreamInfoItem(SERVICE_ID, "https://www.redgifs.com/watch/" + id, title(g),
                    StreamType.VIDEO_STREAM);
            item.setUploaderName(g.getString("userName", ""));
            item.setDuration((long) Math.ceil(g.getDouble("duration", -1)));
            item.setViewCount(g.getLong("views", -1));
            long created = g.getLong("createDate", 0);
            if (created > 0) item.setUploadDate(new DateWrapper(Instant.ofEpochSecond(created), false));
            List<Image> thumbs = new ArrayList<>();
            JsonObject urls = g.getObject("urls");
            String poster = urls.getString("poster", urls.getString("thumbnail", null));
            if (poster != null) thumbs.add(new Image(poster, 360, 640, Image.ResolutionLevel.MEDIUM));
            item.setThumbnails(thumbs);
            p.items.add(item);
        }
        if (!p.items.isEmpty() && page < root.getInt("pages", 0)) p.next = Integer.toString(page + 1);
        return p;
    }

    public static Media resolve(String circuit, String url) throws IOException {
        Matcher m = VIDEO.matcher(url);
        if (!m.find()) throw new IOException("not a video link");
        JsonObject g = call(circuit, "gifs/" + m.group(1).toLowerCase(), "redgifs gif").getObject("gif");
        Media out = new Media();
        out.title = title(g);
        out.author = g.getString("userName", "");
        JsonObject urls = g.getObject("urls");
        out.mp4 = urls.getString("hd", urls.getString("sd", null));
        if (out.mp4 == null) throw new IOException("no stream");
        return out;
    }

    private static String title(JsonObject g) {
        String d = g.getString("description", null);
        if (d != null && !d.trim().isEmpty()) return Web.unescape(d).split("\n")[0];
        JsonArray tags = g.getArray("tags");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.size() && i < 6; i++) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(tags.getString(i));
        }
        return sb.length() > 0 ? sb.toString() : g.getString("id", "");
    }

    private static String token(String circuit) throws IOException {
        synchronized (TOKENS) {
            String[] t = TOKENS.get(circuit);
            if (t != null && System.currentTimeMillis() < Long.parseLong(t[1])) return t[0];
        }
        try {
            String token = JsonParser.object().from(Web.get(circuit, API + "auth/temporary", "redgifs token", null))
                    .getString("token", null);
            if (token == null) throw new IOException("no redgifs token");
            synchronized (TOKENS) {
                TOKENS.put(circuit, new String[] {token, Long.toString(System.currentTimeMillis() + TOKEN_LIFE_MS)});
            }
            return token;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Captcha needed (redgifs token answer is not JSON)");
        }
    }

    private static JsonObject call(String circuit, String path, String label) throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token(circuit));
        try {
            return JsonParser.object().from(Web.get(circuit, API + path, label, headers));
        } catch (IOException e) {
            if (String.valueOf(e.getMessage()).contains("401")) {
                synchronized (TOKENS) {
                    TOKENS.remove(circuit);
                }
            }
            throw e;
        } catch (Exception e) {
            throw new IOException("Captcha needed (redgifs answer is not JSON)");
        }
    }
}
