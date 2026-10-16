package com.orvid.adult;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Txxx {

    public static final int SERVICE_ID = 111;
    private static final String HOST = "txxx.com";
    private static final Pattern VIDEO = Pattern.compile(
            "https?://(?:www\\.)?((?:txxx|hclips|hdzog|hotmovs|inporn|upornia|vxxx|vjav|voyeurhit|tubepornclassic|privatehomeclips)\\.(?:com|tube))/(?:videos?[/-]|embed/)(\\d+)");

    private Txxx() {
    }

    public static final class Page {
        public final List<StreamInfoItem> items = new ArrayList<>();
        public String next;
    }

    public static final class Media {
        public String title = "";
        public String author = "";
        public String mp4;
        public String referer;
    }

    public static boolean handles(String url) {
        return url != null && VIDEO.matcher(url).find();
    }

    public static Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 1 : Integer.parseInt(next);
        String url = "https://" + HOST + "/api/videos2.php?params=86400/str/relevance/60/search.." + page + ".all..&s="
                + URLEncoder.encode(query, "UTF-8");
        JsonObject root = json(Web.get(circuit, url, "txxx search", null));
        Page p = new Page();
        JsonArray videos = root.getArray("videos");
        for (int i = 0; i < videos.size(); i++) {
            JsonObject v = videos.getObject(i);
            String id = v.getString("video_id", null);
            if (id == null) continue;
            String link = "https://" + HOST + "/videos/" + id + "/" + v.getString("dir", "") + "/";
            StreamInfoItem item = new StreamInfoItem(SERVICE_ID, link, Web.unescape(v.getString("title", "")),
                    StreamType.VIDEO_STREAM);
            item.setUploaderName(v.getString("username", ""));
            item.setDuration(Web.clock(v.getString("duration", null)));
            item.setViewCount(Web.number(v.getString("video_viewed", "-1")));
            String date = v.getString("post_date", null);
            com.orvid.search.Dates.set(item, date);
            List<Image> thumbs = new ArrayList<>();
            String scr = v.getString("scr", null);
            if (scr != null) thumbs.add(new Image(scr, 240, 320, Image.ResolutionLevel.MEDIUM));
            item.setThumbnails(thumbs);
            p.items.add(item);
        }
        int pages;
        try {
            pages = Integer.parseInt(String.valueOf(root.get("pages")));
        } catch (Exception e) {
            pages = 0;
        }
        if (!p.items.isEmpty() && page < pages) p.next = Integer.toString(page + 1);
        return p;
    }

    public static Media resolve(String circuit, String url) throws IOException {
        Matcher m = VIDEO.matcher(url);
        if (!m.find()) throw new IOException("not a video link");
        String host = m.group(1);
        String id = m.group(2);
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", url);
        headers.put("X-Requested-With", "XMLHttpRequest");
        JsonArray files;
        try {
            files = JsonParser.array().from(Web.get(circuit, "https://" + host + "/api/videofile.php?video_id=" + id
                    + "&lifetime=8640000", "txxx file", headers));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Captcha needed (txxx answered a page instead of JSON)");
        }
        Media out = new Media();
        out.referer = url;
        for (int i = 0; i < files.size() && out.mp4 == null; i++) {
            String enc = files.getObject(i).getString("video_url", null);
            if (enc == null) continue;
            String path = decode(enc);
            out.mp4 = path.startsWith("http") ? path : "https://" + host + path;
        }
        if (out.mp4 == null) throw new IOException("no stream");
        try {
            long n = Long.parseLong(id);
            String slug = (n / 1000000 * 1000000) + "/" + (n / 1000 * 1000);
            JsonObject info = json(Web.get(circuit, "https://" + host + "/api/json/video/86400/" + slug + "/" + id + ".json",
                    "txxx info", headers)).getObject("video");
            out.title = Web.unescape(info.getString("title", ""));
            out.author = info.getObject("user").getString("username", "");
        } catch (Exception ignored) {
        }
        return out;
    }

    static String decode(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\u0405': sb.append('S'); break;
                case '\u0406': sb.append('I'); break;
                case '\u0408': sb.append('J'); break;
                case '\u0410': sb.append('A'); break;
                case '\u0412': sb.append('B'); break;
                case '\u0415': sb.append('E'); break;
                case '\u041a': sb.append('K'); break;
                case '\u041c': sb.append('M'); break;
                case '\u041d': sb.append('H'); break;
                case '\u041e': sb.append('O'); break;
                case '\u0420': sb.append('P'); break;
                case '\u0421': sb.append('C'); break;
                case '\u0425': sb.append('X'); break;
                case ',': sb.append('/'); break;
                case '.': sb.append('+'); break;
                case '~': sb.append('='); break;
                default: sb.append(c);
            }
        }
        return new String(Base64.getDecoder().decode(sb.toString()), StandardCharsets.UTF_8);
    }

    private static JsonObject json(String text) throws IOException {
        try {
            return JsonParser.object().from(text);
        } catch (Exception e) {
            throw new IOException("Captcha needed (txxx answered a page instead of JSON)");
        }
    }
}
