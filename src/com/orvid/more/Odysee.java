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
        java.util.List<String> claims = new java.util.ArrayList<>();
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
            claims.add("lbry://" + slug + "#" + claim);
        }
        if (hits.size() >= PAGE) p.next = Integer.toString(from + PAGE);
        playable(circuit, p.items, claims);
        return p;
    }

    private static final long PROBE_WAIT_MS = 3000;
    private static final java.util.Map<String, Direct> CHECKED = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, Direct>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Direct> eldest) {
                    return size() > 200;
                }
            });
    private static final java.util.concurrent.ExecutorService PROBES = java.util.concurrent.Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "orvid-odysee-probe");
        t.setDaemon(true);
        return t;
    });

    private void playable(String circuit, java.util.List<StreamInfoItem> items, java.util.List<String> claims) {
        if (items.isEmpty()) return;
        java.util.Map<String, String> streams = new java.util.HashMap<>();
        try {
            StringBuilder urls = new StringBuilder();
            for (String c : claims) urls.append(urls.length() == 0 ? "" : ",").append('"').append(c.replace("\"", "")).append('"');
            JsonObject result = post(circuit, "https://api.na-backend.odysee.com/api/v1/proxy?m=resolve", "application/json",
                    "{\"jsonrpc\":\"2.0\",\"method\":\"resolve\",\"params\":{\"urls\":[" + urls + "]}}").getObject("result");
            for (String c : claims) {
                try {
                    JsonObject claim = result.getObject(c);
                    String sd = claim.getObject("value").getObject("source").getString("sd_hash", "");
                    String id = claim.getString("claim_id", "");
                    if (sd.length() >= 6 && !id.isEmpty()) streams.put(c, "https://player.odycdn.com/v6/streams/" + id + "/" + sd.substring(0, 6) + ".mp4");
                } catch (RuntimeException ignored) {
                }
            }
        } catch (Exception e) {
            com.orvid.net.Diag.mark("odysee resolve batch FAIL " + e.getMessage());
            return;
        }
        java.util.Map<String, java.util.concurrent.Future<Integer>> codes = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, String> e : streams.entrySet()) {
            String url = e.getValue();
            codes.put(e.getKey(), PROBES.submit(() -> probe(circuit, url)));
        }
        long deadline = System.currentTimeMillis() + PROBE_WAIT_MS;
        java.util.List<StreamInfoItem> good = new java.util.ArrayList<>();
        java.util.List<StreamInfoItem> unknown = new java.util.ArrayList<>();
        int refused = 0;
        for (int i = 0; i < items.size(); i++) {
            java.util.concurrent.Future<Integer> f = codes.get(claims.get(i));
            int code = 0;
            if (f != null) {
                try {
                    code = f.get(Math.max(1, deadline - System.currentTimeMillis()), java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    f.cancel(true);
                }
            }
            if (code == 200 || code == 206) {
                StreamInfoItem it = items.get(i);
                good.add(it);
                CHECKED.put(it.getUrl(), new Direct(it.getName(), it.getUploaderName(), streams.get(claims.get(i)), false, REFERER));
            }
            else if (code == 401 || code == 403 || code == 404 || code == 429) refused++;
            else unknown.add(items.get(i));
        }
        com.orvid.net.Diag.mark("odysee playable " + good.size() + ", refused " + refused + ", unknown " + unknown.size());
        items.clear();
        items.addAll(good);
        items.addAll(unknown);
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
        Direct known = CHECKED.get(url);
        if (known != null) return new Direct(known.title, known.author, known.url, false, REFERER);
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
        if (code == 401 || code == 403 || code == 429) {
            throw new com.orvid.ext.ExtSource.Gone("Odysee refused this video (HTTP " + code + ")");
        }
        return new Direct(title, author, stream, stream.contains(".m3u8"), REFERER);
    }
}
