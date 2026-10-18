package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.orvid.play.Direct;
import com.orvid.search.Source;


import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OkRu extends Site {

    private static final String MOBILE = "Mozilla/5.0 (Android 14; Mobile; rv:140.0) Gecko/140.0 Firefox/140.0";
    private static final Pattern RESULTS = Pattern.compile("<video-search-results[^>]*\\svideos=\"([^\"]+)\"");
    private static final Pattern OPTIONS = Pattern.compile("data-options=\"([^\"]+)\"");

    OkRu() {
        super(181, "OK", "ok\\.ru/video(?:embed)?/(\\d+)");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        String html = text(circuit, "https://m.ok.ru/video/search?st.query=" + enc(query), null, null, "User-Agent", MOBILE);
        Matcher m = RESULTS.matcher(html);
        Source.Page p = new Source.Page();
        if (!m.find()) return p;
        JsonArray data = parse(unescape(m.group(1))).getObject("table").getArray("data");
        for (int i = 0; i < data.size(); i++) {
            JsonObject v = data.getObject(i);
            String id = v.getString("id", null);
            if (id == null || v.getInt("accessRights", 0) != 0) continue;
            p.items.add(item("https://ok.ru/video/" + id, v.getString("name", ""), v.getString("ownerName", ""),
                    v.getLong("duration", -1) / 1000, v.getLong("totalViews", -1), v.getObject("thumbnails").getString("HIGH", null)));
        }
        return p;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        Matcher m = OPTIONS.matcher(text(circuit, "https://ok.ru/videoembed/" + id(url), null, null));
        if (!m.find()) throw new IOException("video unavailable");
        JsonObject vars = parse(unescape(m.group(1))).getObject("flashvars");
        Object raw = vars.get("metadata");
        JsonObject meta = raw instanceof JsonObject ? (JsonObject) raw : parse(String.valueOf(raw));
        String title = meta.getObject("movie").getString("title", "");
        String author = meta.getObject("author").getString("name", "");
        String hls = meta.getString("hlsManifestUrl", null);
        if (hls != null) return new Direct(title, author, hls, true, null);
        JsonArray videos = meta.getArray("videos");
        if (videos.isEmpty()) throw new IOException("video unavailable");
        return new Direct(title, author, videos.getObject(videos.size() - 1).getString("url"), false, null);
    }

    private static String unescape(String s) {
        return s.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static JsonObject parse(String s) throws IOException {
        try {
            return JsonParser.object().from(s);
        } catch (Exception e) {
            throw new IOException("OK answer is not JSON");
        }
    }
}
