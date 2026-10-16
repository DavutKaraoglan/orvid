package com.orvid.ep;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.orvid.net.Fetch;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Eporner {

    public static final int SERVICE_ID = 108;
    private static final String SITE = "https://www.eporner.com";
    private static final int PAGE = 30;
    private static final Pattern VIDEO = Pattern.compile("eporner\\.com/(?:video-|hd-porn/)([A-Za-z0-9]+)");
    private static final Pattern DLOAD = Pattern.compile("href=\"(/dload/[^\"]+-(\\d+)p\\.mp4)\"");
    private static final Pattern TITLE = Pattern.compile("<h1[^>]*>([^<]+)");

    private Eporner() {
    }

    public static final class Page {
        public final List<StreamInfoItem> items = new ArrayList<>();
        public String next;
    }

    public static final class Media {
        public String title = "";
        public String mp4;
    }

    public static boolean handles(String url) {
        return url != null && VIDEO.matcher(url).find();
    }

    public static Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 1 : Integer.parseInt(next);
        String url = SITE + "/api/v2/video/search/?query=" + URLEncoder.encode(query, "UTF-8") + "&per_page=" + PAGE
                + "&page=" + page + "&thumbsize=big&order=most-popular&format=json";
        JsonObject root;
        try {
            root = JsonParser.object().from(com.orvid.adult.Web.get(circuit, url, "eporner search", null));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Captcha needed (eporner answered a page instead of JSON)");
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
            item.setDuration(v.getLong("length_sec", -1));
            item.setViewCount(v.getLong("views", -1));
            String added = v.getString("added", null);
            com.orvid.search.Dates.set(item, added);
            List<Image> thumbs = new ArrayList<>();
            JsonObject thumb = v.getObject("default_thumb");
            String src = thumb.getString("src", null);
            if (src != null) thumbs.add(new Image(src, thumb.getInt("height", 360), thumb.getInt("width", 640),
                    Image.ResolutionLevel.MEDIUM));
            item.setThumbnails(thumbs);
            p.items.add(item);
        }
        if (page < root.getInt("total_pages", 0)) p.next = Integer.toString(page + 1);
        return p;
    }

    public static Media resolve(String circuit, String url) throws IOException {
        if (!handles(url)) throw new IOException("not a video link");
        String html = com.orvid.adult.Web.get(circuit, url, "eporner video", null);
        Media m = new Media();
        Matcher t = TITLE.matcher(html);
        if (t.find()) m.title = unescape(t.group(1).trim());
        int cap = Fetch.torMode() ? 720 : 1080;
        int best = -1;
        Matcher d = DLOAD.matcher(html);
        while (d.find()) {
            int h = Integer.parseInt(d.group(2));
            if (h <= cap && h > best) {
                best = h;
                m.mp4 = SITE + d.group(1);
            }
        }
        if (m.mp4 == null) throw new IOException("no stream");
        m.mp4 = cdn(circuit, m.mp4, url);
        return m;
    }

    private static String cdn(String circuit, String dload, String page) throws IOException {
        try {
            return Fetch.on(circuit, () -> {
                java.net.HttpURLConnection c = Fetch.open(dload);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", Fetch.USER_AGENT);
                c.setRequestProperty("Referer", page);
                int code = c.getResponseCode();
                String location = c.getHeaderField("Location");
                c.disconnect();
                if (code / 100 != 3 || location == null) return dload;
                return location.replace(" ", "%20");
            });
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    static String unescape(String s) {
        return s.replace("&quot;", "\"").replace("&#039;", "'").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">");
    }

}
