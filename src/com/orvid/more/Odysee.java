package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;

final class Odysee extends Site {

    private static final int PAGE = 20;

    Odysee() {
        super(136, "Odysee", "odysee\\.com/(@[^/?#]+/[^?#]+|[^@/?#][^/?#]*)");
    }

    private static final String REFERER = "https://odysee.com/";

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        int from = next == null ? 0 : Integer.parseInt(next);
        JsonArray hits = array(circuit, "https://lighthouse.odysee.tv/search?s=" + enc(query) + "&size=" + PAGE
                + "&from=" + from + "&mediaType=video&free_only=true&nsfw=false&resolve=true");
        Source.Page p = new Source.Page();
        for (int i = 0; i < hits.size(); i++) {
            JsonObject h = hits.getObject(i);
            String claim = h.getString("claimId", null);
            String slug = h.getString("name", null);
            if (claim == null || slug == null) continue;
            String channel = h.getString("channel", "");
            String path = channel.isEmpty() ? slug + ":" + claim : channel + ":" + h.getString("channel_claim_id", "") + "/" + slug + ":" + claim;
            StreamInfoItem item = item("https://odysee.com/" + path, h.getString("title", slug),
                    channel.startsWith("@") ? channel.substring(1) : channel, h.getLong("duration", -1), -1,
                    small(h.getString("thumbnail_url", null)));
            com.orvid.search.Dates.set(item, h.getString("release_time", null));
            p.items.add(item);
        }
        if (hits.size() >= PAGE) p.next = Integer.toString(from + PAGE);
        return p;
    }

    private static int probe(String circuit, String url) {
        try {
            return com.orvid.net.Fetch.on(circuit, () -> {
                java.net.HttpURLConnection c = com.orvid.net.Fetch.open(url);
                c.setRequestProperty("User-Agent", com.orvid.net.Fetch.USER_AGENT);
                c.setRequestProperty("Range", "bytes=0-0");
                c.setRequestProperty("Referer", REFERER);
                int code = c.getResponseCode();
                c.disconnect();
                return code;
            });
        } catch (Exception e) {
            return 0;
        }
    }

    private static String small(String url) {
        return url == null || url.isEmpty() ? url : "https://thumbnails.odycdn.com/optimize/s:390:220/quality:85/plain/" + url;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        String path = id(url);
        String last = path.substring(path.lastIndexOf('/') + 1);
        int colon = last.lastIndexOf(':');
        String uri = "lbry://" + (colon > 0 ? last.substring(0, colon) + "#" + last.substring(colon + 1) : last);
        String rpc = "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":%s}";
        JsonObject get = post(circuit, "https://api.na-backend.odysee.com/api/v1/proxy?m=get", "application/json",
                String.format(rpc, "get", "{\"uri\":\"" + uri + "\",\"save_file\":false}")).getObject("result");
        String stream = get.getString("streaming_url", null);
        if (stream == null) throw new IOException("video unavailable");
        String title = last.substring(0, colon > 0 ? colon : last.length()).replace('-', ' ');
        String author = "";
        try {
            JsonObject claim = post(circuit, "https://api.na-backend.odysee.com/api/v1/proxy?m=resolve", "application/json",
                    String.format(rpc, "resolve", "{\"urls\":[\"" + uri + "\"]}")).getObject("result").getObject(uri);
            title = claim.getObject("value").getString("title", title);
            author = claim.getObject("signing_channel").getObject("value").getString("title",
                    claim.getObject("signing_channel").getString("name", ""));
        } catch (IOException ignored) {
        }
        int code = probe(circuit, stream);
        if (code == 401 || code == 403 || code == 429) throw new IOException("Odysee is not serving this video right now (HTTP " + code + ")");
        return new Direct(title, author, stream, stream.contains(".m3u8"), REFERER);
    }
}
