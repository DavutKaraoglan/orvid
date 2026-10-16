package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;

final class Aparat extends Site {

    private static final String API = "https://www.aparat.com/api/fa/v1/video/video/";

    Aparat() {
        super(139, "Aparat", "aparat\\.com/(?:v/|video/video/embed/videohash/)([A-Za-z0-9]+)");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        JsonObject root = json(circuit, next != null ? next : API + "search/text/" + enc(query).replace("+", "%20"));
        Source.Page p = new Source.Page();
        JsonArray included = root.getArray("included");
        for (int i = 0; i < included.size(); i++) {
            JsonObject o = included.getObject(i);
            if (!"Video".equals(o.getString("type", null))) continue;
            JsonObject v = o.getObject("attributes");
            String uid = v.getString("uid", null);
            if (uid == null || v.getBoolean("isHidden", false)) continue;
            StreamInfoItem item = item("https://www.aparat.com/v/" + uid, v.getString("title", ""),
                    v.getString("sender_name", v.getString("username", "")), number(v.get("duration")),
                    number(v.get("visit_cnt_int")), v.getString("medium_poster", v.getString("small_poster", null)));
            String when = v.getString("sdate_rss", null);
            if (when != null) com.orvid.search.Dates.set(item, when.replace(' ', 'T'));
            p.items.add(item);
        }
        JsonArray data = root.getArray("data");
        if (!data.isEmpty()) {
            Object link = data.getObject(0).getObject("attributes").get("link");
            if (link instanceof JsonObject) p.next = ((JsonObject) link).getString("next", null);
        }
        return p;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        JsonObject v = json(circuit, API + "show/videohash/" + id(url) + "?pr=1&mf=1").getObject("data").getObject("attributes");
        String best = null;
        int bestHeight = -1;
        JsonArray files = v.getArray("file_link_all");
        for (int i = 0; i < files.size(); i++) {
            JsonObject f = files.getObject(i);
            int h = (int) number(f.getString("profile", ""));
            JsonArray urls = f.getArray("urls");
            if (urls.isEmpty() || h > 720) continue;
            if (h > bestHeight) {
                bestHeight = h;
                best = urls.getString(0);
            }
        }
        String title = v.getString("title", "");
        String author = v.getString("sender_name", v.getString("username", ""));
        if (best != null) return new Direct(title, author, best, false, null);
        String hls = v.getString("hls_link", null);
        if (hls == null) throw new IOException("video unavailable");
        return new Direct(title, author, hls, true, null);
    }
}
