package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Ted extends Site {

    private static final int PAGE = 20;
    private static final Pattern NEXT = Pattern.compile("__NEXT_DATA__[^>]*>(.*?)</script>", Pattern.DOTALL);

    Ted() {
        super(152, "TED", "ted\\.com/talks/([A-Za-z0-9_]+)");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 0 : Integer.parseInt(next);
        JsonObject r = post(circuit, "https://www.ted.com/api/search", "application/json",
                "[{\"indexName\":\"relevance\",\"params\":{\"query\":" + BitChute.quote(query) + ",\"hitsPerPage\":" + PAGE
                        + ",\"page\":" + page + "}}]").getArray("results").getObject(0);
        JsonArray hits = r.getArray("hits");
        Source.Page p = new Source.Page();
        for (int i = 0; i < hits.size(); i++) {
            JsonObject h = hits.getObject(i);
            String slug = h.getString("slug", null);
            if (slug == null) continue;
            p.items.add(item("https://www.ted.com/talks/" + slug, h.getString("title", ""), h.getString("speakers", ""),
                    number(h.get("duration")), -1, small(photo(h.getArray("photos")))));
        }
        if (page + 1 < r.getInt("nbPages", 0)) p.next = Integer.toString(page + 1);
        return p;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        Matcher m = NEXT.matcher(text(circuit, "https://www.ted.com/talks/" + id(url), null, null));
        if (!m.find()) throw new IOException("video unavailable");
        JsonObject v;
        try {
            v = JsonParser.object().from(m.group(1)).getObject("props").getObject("pageProps").getObject("videoData");
        } catch (Exception e) {
            throw new IOException("TED page changed");
        }
        String hls = v.getString("hlsUrl", null);
        if (hls == null) throw new IOException("no stream");
        return new Direct(v.getString("title", ""), v.getString("presenterDisplayName", ""), hls, true, null);
    }

    private static String small(String url) {
        if (url == null) return null;
        return "https://pi.tedcdn.com/r/" + url.replaceFirst("^https?://", "") + "?w=480";
    }

    private static String photo(JsonArray photos) {
        String best = null;
        for (int i = 0; i < photos.size() && best == null; i++) {
            JsonArray sizes = photos.getObject(i).getArray("photo_sizes");
            for (int j = 0; j < sizes.size(); j++) {
                JsonObject s = sizes.getObject(j);
                if (best == null || s.getInt("talkstar_aspect_ratio_id", 0) == 2) best = s.getString("url", best);
            }
        }
        return best;
    }
}
