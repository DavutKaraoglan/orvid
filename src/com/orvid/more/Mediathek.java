package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.net.Fetch;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

final class Mediathek extends Site {

    private static final int PAGE = 25;
    private static final java.util.regex.Pattern GEO = java.util.regex.Pattern.compile("/dach/|/de/|geo|/at/|/ch/");

    Mediathek() {
        super(143, "ARD/ZDF Mediathek", "(#mvw=.+)$");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        int offset = next == null ? 0 : Integer.parseInt(next);
        String body = "{\"queries\":[{\"fields\":[\"title\",\"topic\"],\"query\":" + BitChute.quote(query) + "}],"
                + "\"sortBy\":\"timestamp\",\"sortOrder\":\"desc\",\"future\":false,\"offset\":" + offset
                + ",\"size\":" + PAGE + ",\"duration_min\":60}";
        JsonArray results = post(circuit, "https://mediathekviewweb.de/api/query", "text/plain", body)
                .getObject("result").getArray("results");
        Source.Page p = new Source.Page();
        for (int i = 0; i < results.size(); i++) {
            JsonObject r = results.getObject(i);
            String video = r.getString("url_video", null);
            if (video == null) continue;
            String site = r.getString("url_website", "https://mediathekviewweb.de/");
            String channel = r.getString("channel", "");
            String title = r.getString("title", "");
            String topic = r.getString("topic", "");
            String shown = topic.isEmpty() || title.contains(topic) ? title : topic + ": " + title;
            String url = site.replaceAll("#.*$", "") + "#mvw=" + enc(https(video)) + "&t=" + enc(shown) + "&c=" + enc(channel);
            StreamInfoItem item = item(url, shown, channel, r.getLong("duration", -1), -1, null);
            com.orvid.search.Dates.set(item, r.getLong("timestamp", 0));
            p.items.add(item);
        }
        if (results.size() >= PAGE) p.next = Integer.toString(offset + PAGE);
        return p;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        Map<String, String> args = new HashMap<>();
        for (String part : id(url).substring(1).split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) args.put(part.substring(0, eq), URLDecoder.decode(part.substring(eq + 1), "UTF-8"));
        }
        String video = args.get("mvw");
        if (video == null) throw new IOException("no stream");
        Direct d = new Direct(args.get("t"), args.get("c"), video, video.contains(".m3u8"), null);
        return GEO.matcher(video).find() ? d.via(Fetch.dachCircuit()) : d;
    }
}
