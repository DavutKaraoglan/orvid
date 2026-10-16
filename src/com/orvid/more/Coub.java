package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;

final class Coub extends Site {

    Coub() {
        super(145, "Coub", "coub\\.com/(?:view|embed)/([A-Za-z0-9]+)");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 1 : Integer.parseInt(next);
        JsonObject root = json(circuit, "https://coub.com/api/v2/search/coubs?q=" + enc(query) + "&page=" + page);
        JsonArray coubs = root.getArray("coubs");
        Source.Page p = new Source.Page();
        for (int i = 0; i < coubs.size(); i++) {
            JsonObject c = coubs.getObject(i);
            String id = c.getString("permalink", null);
            if (id == null || c.getBoolean("age_restricted", false) || c.getBoolean("not_safe_for_work", false)) continue;
            StreamInfoItem item = item("https://coub.com/view/" + id, c.getString("title", ""),
                    c.getObject("channel").getString("title", ""), Math.round(c.getDouble("duration", -1)),
                    c.getLong("views_count", -1), picture(c));
            com.orvid.search.Dates.set(item, c.getString("published_at", c.getString("created_at", null)));
            p.items.add(item);
        }
        if (page < root.getInt("total_pages", 0)) p.next = Integer.toString(page + 1);
        return p;
    }

    private static String picture(JsonObject c) {
        String template = c.getObject("image_versions").getString("template", null);
        if (template != null && template.contains("%{version}")) return template.replace("%{version}", "med");
        return c.getString("picture", null);
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        JsonObject c = json(circuit, "https://coub.com/api/v2/coubs/" + id(url));
        JsonObject files = c.getObject("file_versions");
        String src = files.getObject("share").getString("default", null);
        if (src == null) src = files.getObject("html5").getObject("video").getObject("high").getString("url", null);
        if (src == null) throw new IOException("video unavailable");
        return new Direct(c.getString("title", ""), c.getObject("channel").getString("title", ""), src, false, null);
    }
}
