package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.net.Fetch;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;

final class Ccc extends Site {

    private static final String API = "https://api.media.ccc.de/public/events/";

    Ccc() {
        super(140, "media.ccc.de", "media\\.ccc\\.de/v/([A-Za-z0-9._-]+)");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 1 : Integer.parseInt(next);
        JsonArray events = json(circuit, API + "search?q=" + enc(query) + "&page=" + page).getArray("events");
        Source.Page p = new Source.Page();
        for (int i = 0; i < events.size(); i++) {
            JsonObject e = events.getObject(i);
            String slug = e.getString("slug", null);
            if (slug == null) continue;
            StreamInfoItem item = item("https://media.ccc.de/v/" + slug, e.getString("title", ""),
                    join(e.getArray("persons")), e.getLong("duration", -1), e.getLong("view_count", -1),
                    e.getString("thumb_url", null));
            com.orvid.search.Dates.set(item, e.getString("date", e.getString("release_date", null)));
            p.items.add(item);
        }
        if (events.size() >= 25) p.next = Integer.toString(page + 1);
        return p;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        JsonObject e = json(circuit, API + id(url));
        String lang = e.getString("original_language", "");
        int cap = Fetch.torMode() ? 720 : 1080;
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        JsonArray recs = e.getArray("recordings");
        for (int i = 0; i < recs.size(); i++) {
            JsonObject r = recs.getObject(i);
            String mime = r.getString("mime_type", "");
            if (!mime.startsWith("video/") || r.getString("recording_url", null) == null) continue;
            int h = r.getInt("height", 0);
            int score = h <= cap ? h : -h;
            if (mime.equals("video/mp4")) score += 5;
            if (lang.equals(r.getString("language", ""))) score += 2000;
            if (score > bestScore) {
                bestScore = score;
                best = r.getString("recording_url");
            }
        }
        if (best == null) throw new IOException("no stream");
        return new Direct(e.getString("title", ""), join(e.getArray("persons")), best, false, null);
    }

    private static String join(JsonArray a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.size(); i++) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(a.getString(i, ""));
        }
        return sb.toString();
    }
}
