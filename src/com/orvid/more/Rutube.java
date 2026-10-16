package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;

final class Rutube extends Site {

    Rutube() {
        super(138, "Rutube", "rutube\\.ru/(?:video|play/embed)/([0-9a-f]{32})");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 1 : Integer.parseInt(next);
        JsonObject root = json(circuit, "https://rutube.ru/api/search/video/?query=" + enc(query) + "&page=" + page + "&format=json");
        JsonArray results = root.getArray("results");
        Source.Page p = new Source.Page();
        for (int i = 0; i < results.size(); i++) {
            JsonObject v = results.getObject(i);
            if (v.getBoolean("is_adult", false) || v.getBoolean("is_hidden", false)) continue;
            String id = v.getString("id", null);
            if (id == null) continue;
            StreamInfoItem item = item("https://rutube.ru/video/" + id + "/", v.getString("title", ""),
                    v.getObject("author").getString("name", ""), v.getLong("duration", -1), v.getLong("hits", -1),
                    small(v.getString("thumbnail_url", null)));
            com.orvid.search.Dates.set(item, v.getString("publication_ts", v.getString("created_ts", null)));
            p.items.add(item);
        }
        if (root.getBoolean("has_next", false)) p.next = Integer.toString(page + 1);
        return p;
    }

    private static String small(String url) {
        return url == null || url.contains("?") ? url : url + "?size=m";
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        JsonObject o = json(circuit, "https://rutube.ru/api/play/options/" + id(url) + "/?no_404=true&referer=&pver=v2");
        String hls = o.getObject("video_balancer").getString("m3u8", null);
        if (hls == null) throw new IOException(o.getString("detail", "video unavailable"));
        return new Direct(o.getString("title", ""), o.getObject("author").getString("name", ""), hls, true, null);
    }
}
