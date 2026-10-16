package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.net.URLDecoder;

final class Nasa extends Site {

    private static final int PAGE = 20;
    private static final String API = "https://images-api.nasa.gov/";

    Nasa() {
        super(142, "NASA", "images\\.nasa\\.gov/details/([^?#]+)");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 1 : Integer.parseInt(next);
        JsonObject col = json(circuit, API + "search?media_type=video&page_size=" + PAGE + "&page=" + page + "&q=" + enc(query))
                .getObject("collection");
        JsonArray items = col.getArray("items");
        Source.Page p = new Source.Page();
        for (int i = 0; i < items.size(); i++) {
            JsonObject it = items.getObject(i);
            if (it.getArray("data").isEmpty()) continue;
            JsonObject d = it.getArray("data").getObject(0);
            String id = d.getString("nasa_id", null);
            if (id == null) continue;
            String thumb = null;
            JsonArray links = it.getArray("links");
            for (int j = 0; j < links.size() && thumb == null; j++) {
                if ("preview".equals(links.getObject(j).getString("rel", null))) thumb = links.getObject(j).getString("href", null);
            }
            StreamInfoItem item = item("https://images.nasa.gov/details/" + enc(id).replace("+", "%20"), d.getString("title", id),
                    d.getString("center", "NASA"), -1, -1, thumb == null ? null : thumb.replace(" ", "%20"));
            com.orvid.search.Dates.set(item, d.getString("date_created", null));
            p.items.add(item);
        }
        JsonArray links = col.getArray("links");
        for (int i = 0; i < links.size(); i++) {
            if ("next".equals(links.getObject(i).getString("rel", null))) p.next = Integer.toString(page + 1);
        }
        return p;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        String id = URLDecoder.decode(id(url), "UTF-8");
        String path = enc(id).replace("+", "%20");
        JsonArray items = json(circuit, API + "asset/" + path).getObject("collection").getArray("items");
        String[] order = {"~medium.mp4", "~large.mp4", "~mobile.mp4", "~small.mp4", "~orig.mp4", ".mp4"};
        String best = null;
        for (String suffix : order) {
            for (int i = 0; i < items.size() && best == null; i++) {
                String href = items.getObject(i).getString("href", "");
                if (href.endsWith(suffix)) best = href;
            }
            if (best != null) break;
        }
        if (best == null) throw new IOException("no stream");
        String title = id;
        String center = "NASA";
        try {
            JsonArray found = json(circuit, API + "search?nasa_id=" + path).getObject("collection").getArray("items");
            if (!found.isEmpty()) {
                JsonObject d = found.getObject(0).getArray("data").getObject(0);
                title = d.getString("title", id);
                center = d.getString("center", center);
            }
        } catch (IOException ignored) {
        }
        return new Direct(title, center, https(best).replace(" ", "%20"), false, null);
    }
}
