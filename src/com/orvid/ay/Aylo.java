package com.orvid.ay;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.orvid.net.Diag;
import com.orvid.net.Fetch;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Aylo {

    public static final int SERVICE_ID = 107;
    private static final String SITE = "https://www.pornhub.com";
    public static final String REFERER = SITE + "/";
    private static final Pattern VIDEO = Pattern.compile("pornhub\\.com/view_video\\.php\\?viewkey=([A-Za-z0-9]+)");
    private static final Pattern FLASHVARS = Pattern.compile("var flashvars(?:_\\d+)?\\s*=\\s*\\{");

    private Aylo() {
    }

    public static final class Page {
        public final List<StreamInfoItem> items = new ArrayList<>();
        public String next;
    }

    public static final class Media {
        public String title = "";
        public String hls;
    }

    public static boolean handles(String url) {
        return url != null && VIDEO.matcher(url).find();
    }

    public static Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 1 : Integer.parseInt(next);
        String url = SITE + "/webmasters/search?search=" + URLEncoder.encode(query, "UTF-8") + "&page=" + page;
        JsonObject root;
        try {
            root = JsonParser.object().from(com.orvid.adult.Web.get(circuit, url, "aylo search", null));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Captcha needed (aylo answered a page instead of JSON)");
        }
        Page p = new Page();
        JsonArray videos = root.getArray("videos");
        for (int i = 0; i < videos.size(); i++) {
            JsonObject v = videos.getObject(i);
            String link = v.getString("url", null);
            if (!handles(link)) continue;
            StreamInfoItem item = new StreamInfoItem(SERVICE_ID, link, unescape(v.getString("title", "")),
                    StreamType.VIDEO_STREAM);
            item.setUploaderName("");
            item.setDuration(seconds(v.getString("duration", "")));
            try {
                item.setViewCount(Long.parseLong(v.getString("views", "-1")));
            } catch (NumberFormatException e) {
                item.setViewCount(-1);
            }
            List<Image> thumbs = new ArrayList<>();
            String thumb = v.getString("default_thumb", null);
            if (thumb != null) thumbs.add(new Image(thumb, 360, 640, Image.ResolutionLevel.MEDIUM));
            item.setThumbnails(thumbs);
            p.items.add(item);
        }
        if (videos.size() >= 20) p.next = Integer.toString(page + 1);
        return p;
    }

    private static final java.util.concurrent.ExecutorService POOL = java.util.concurrent.Executors.newCachedThreadPool();

    public static Media resolve(String circuit, String url) throws IOException {
        IOException last = null;
        for (int round = 1; round <= 3; round++) {
            java.util.concurrent.CompletionService<Media> done = new java.util.concurrent.ExecutorCompletionService<>(POOL);
            List<java.util.concurrent.Future<Media>> jobs = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                jobs.add(done.submit(() -> {
                    Media m = resolveOnce(circuit, url);
                    int code = status(circuit, m.hls);
                    if (code != 200) throw new IOException("Captcha needed (aylo playlist " + code + ")");
                    return m;
                }));
            }
            try {
                for (int i = 0; i < jobs.size(); i++) {
                    try {
                        Media m = done.take().get();
                        Diag.mark("aylo playlist ok in round " + round);
                        return m;
                    } catch (java.util.concurrent.ExecutionException e) {
                        last = e.getCause() instanceof IOException ? (IOException) e.getCause() : new IOException(e.getCause());
                    }
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            } finally {
                for (java.util.concurrent.Future<Media> f : jobs) f.cancel(true);
            }
        }
        throw last;
    }

    private static int status(String circuit, String url) throws IOException {
        try {
            return Fetch.on(circuit, () -> {
                HttpURLConnection c = Fetch.open(url);
                c.setRequestProperty("User-Agent", Fetch.USER_AGENT);
                c.setRequestProperty("Referer", REFERER);
                c.setRequestProperty("Origin", SITE);
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                int code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                if (in != null) in.close();
                return code;
            });
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static Media resolveOnce(String circuit, String url) throws IOException {
        Matcher key = VIDEO.matcher(url);
        if (!key.find()) throw new IOException("not a video link");
        String html = com.orvid.adult.Web.get(circuit, SITE + "/embed/" + key.group(1), "aylo video", null);
        Matcher m = FLASHVARS.matcher(html);
        String json = m.find() ? object(html, m.end() - 1) : null;
        if (json == null) {
            if (html.contains("mediaDefinitions") || !html.contains("flashvars")) {
                throw new IOException("Captcha needed (aylo video page without player)");
            }
            throw new IOException("video unavailable");
        }
        JsonObject vars;
        try {
            vars = JsonParser.object().from(json);
        } catch (Exception e) {
            throw new IOException("video unavailable");
        }
        Media out = new Media();
        out.title = unescape(vars.getString("video_title", ""));
        int cap = Fetch.torMode() ? 720 : 1080;
        int best = -1;
        JsonArray defs = vars.getArray("mediaDefinitions");
        for (int i = 0; i < defs.size(); i++) {
            JsonObject d = defs.getObject(i);
            if (!"hls".equals(d.getString("format", ""))) continue;
            int q;
            try {
                q = Integer.parseInt(String.valueOf(d.get("quality")));
            } catch (NumberFormatException e) {
                continue;
            }
            if (q <= cap && q > best) {
                best = q;
                out.hls = d.getString("videoUrl", null);
            }
        }
        if (out.hls == null) throw new IOException("no stream");
        return out;
    }

    public static String object(String text, int start) {
        int depth = 0;
        boolean string = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (string) {
                if (c == '\\') i++;
                else if (c == '"') string = false;
            } else if (c == '"') {
                string = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return text.substring(start, i + 1);
            }
        }
        return null;
    }

    static long seconds(String text) {
        long total = 0;
        try {
            for (String part : text.trim().split(":")) total = total * 60 + Long.parseLong(part);
            return total;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String unescape(String s) {
        return s.replace("&quot;", "\"").replace("&#039;", "'").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">");
    }

}
