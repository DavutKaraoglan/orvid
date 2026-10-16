package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;

final class BitChute extends Site {

    private static final int PAGE = 20;
    private static final String API = "https://api.bitchute.com/api/beta/";

    BitChute() {
        super(137, "BitChute", "bitchute\\.com/(?:video|embed)/([A-Za-z0-9_-]+)");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        int offset = next == null ? 0 : Integer.parseInt(next);
        JsonObject root = post(circuit, API + "search/videos", "application/json",
                "{\"offset\":" + offset + ",\"limit\":" + PAGE + ",\"query\":" + quote(query)
                        + ",\"sensitivity_id\":\"normal\",\"sort\":\"new\"}");
        JsonArray videos = root.getArray("videos");
        Source.Page p = new Source.Page();
        for (int i = 0; i < videos.size(); i++) {
            JsonObject v = videos.getObject(i);
            String id = v.getString("video_id", null);
            if (id == null) continue;
            StreamInfoItem item = item("https://www.bitchute.com/video/" + id + "/", v.getString("video_name", ""),
                    v.getObject("channel").getString("channel_name", ""), clock(v.getString("duration", null)),
                    v.getLong("view_count", -1), v.getString("thumbnail_url", null));
            com.orvid.search.Dates.set(item, v.getString("date_published", null));
            p.items.add(item);
        }
        if (videos.size() >= PAGE) p.next = Integer.toString(offset + PAGE);
        return p;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        String id = id(url);
        String body = "{\"video_id\":\"" + id + "\"}";
        JsonObject media = post(circuit, API + "video/media", "application/json", body);
        String src = media.getString("media_url", null);
        if (src == null) throw new IOException("video unavailable");
        String title = "";
        String author = "";
        try {
            JsonObject info = post(circuit, API + "video", "application/json", body);
            title = clean(info.getString("video_name", ""));
            author = info.getObject("channel").getString("channel_name", "");
        } catch (IOException ignored) {
        }
        return new Direct(title, author, src, src.contains(".m3u8"), null);
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') sb.append('\\').append(ch);
            else if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
            else sb.append(ch);
        }
        return sb.append('"').toString();
    }
}
