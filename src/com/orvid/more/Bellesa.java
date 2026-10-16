package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;

final class Bellesa extends Site {

    private static final int PAGE = 24;
    private static final String API = "https://www.bellesa.co/api/rest/v1/videos";

    Bellesa() {
        super(158, "Bellesa", "bellesa\\.co/videos/(\\d+)");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 1 : Integer.parseInt(next);
        JsonArray videos = array(circuit, API + "/search?q=" + enc(query) + "&limit=" + PAGE + "&page=" + page);
        Source.Page p = new Source.Page();
        for (int i = 0; i < videos.size(); i++) {
            JsonObject v = videos.getObject(i);
            if (v.getObject("access").getInt("plus", 0) != 0 || v.getString("source", null) == null) continue;
            JsonArray providers = v.getArray("content_provider");
            String author = providers.isEmpty() ? "Bellesa" : providers.getObject(0).getString("name", "Bellesa");
            StreamInfoItem item = item("https://www.bellesa.co/videos/" + v.getLong("id"), v.getString("title", ""), author,
                    v.getLong("duration", -1), v.getLong("views", -1),
                    v.getString("image", "").replace("/image/upload/", "/image/upload/w_480,q_70/"));
            com.orvid.search.Dates.set(item, v.getLong("posted_on", 0));
            p.items.add(item);
        }
        if (videos.size() >= PAGE) p.next = Integer.toString(page + 1);
        return p;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        JsonObject v = json(circuit, API + "/" + id(url));
        if (v.getObject("access").getInt("plus", 0) != 0) throw new IOException("members only");
        String source = v.getString("source", null);
        if (source == null) throw new IOException("video unavailable");
        int best = 0;
        for (String r : v.getString("resolutions", "").split(",")) {
            try {
                int h = Integer.parseInt(r.trim());
                if (h <= 720 && h > best) best = h;
            } catch (NumberFormatException ignored) {
            }
        }
        if (best == 0) best = 480;
        JsonArray providers = v.getArray("content_provider");
        String author = providers.isEmpty() ? "Bellesa" : providers.getObject(0).getString("name", "Bellesa");
        return new Direct(v.getString("title", ""), author, "https://s.bellesa.co/v/" + source + "/" + best + ".mp4", false, null);
    }
}
