package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.text.Normalizer;

final class Mastodon extends Site {

    private static final String HOST = "https://mastodon.social";
    private static final int PAGE = 40;

    Mastodon() {
        super(150, "Mastodon", "mastodon\\.social/@[^/]+/(\\d+)");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        String tag = tag(query);
        if (tag.isEmpty()) return new Source.Page();
        JsonArray statuses = array(circuit, HOST + "/api/v1/timelines/tag/" + enc(tag) + "?only_media=true&limit=" + PAGE
                + (next != null ? "&max_id=" + next : ""));
        Source.Page p = new Source.Page();
        String last = null;
        for (int i = 0; i < statuses.size(); i++) {
            JsonObject s = statuses.getObject(i);
            last = s.getString("id", last);
            JsonObject media = video(s);
            if (media == null || s.getBoolean("sensitive", false)) continue;
            JsonObject account = s.getObject("account");
            String text = clean(s.getString("content", "").replaceAll("<br\\s*/?>|</p>", " ")).replaceAll("\\s+", " ");
            if (text.length() > 140) text = text.substring(0, 140) + "...";
            String title = text.isEmpty() ? media.getString("description", "#" + tag) : text;
            StreamInfoItem item = item(HOST + "/@" + account.getString("acct", "") + "/" + s.getString("id"), title,
                    account.getString("display_name", account.getString("acct", "")),
                    (long) media.getObject("meta").getObject("original").getDouble("duration", -1), -1,
                    media.getString("preview_url", null));
            com.orvid.search.Dates.set(item, s.getString("created_at", null));
            p.items.add(item);
        }
        if (statuses.size() >= PAGE && last != null) p.next = last;
        return p;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        JsonObject s = json(circuit, HOST + "/api/v1/statuses/" + id(url));
        JsonObject media = video(s);
        if (media == null) throw new IOException("no video");
        String text = clean(s.getString("content", "").replaceAll("<br\\s*/?>|</p>", " ")).replaceAll("\\s+", " ");
        if (text.length() > 140) text = text.substring(0, 140) + "...";
        JsonObject account = s.getObject("account");
        return new Direct(text, account.getString("display_name", account.getString("acct", "")),
                media.getString("url"), false, null);
    }

    private static JsonObject video(JsonObject status) {
        JsonArray media = status.getArray("media_attachments");
        for (int i = 0; i < media.size(); i++) {
            JsonObject m = media.getObject(i);
            String type = m.getString("type", "");
            if (type.equals("video") && m.getString("url", null) != null) return m;
        }
        return null;
    }

    static String tag(String query) {
        String folded = Normalizer.normalize(query.replace("ı", "i").replace("İ", "I"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        StringBuilder sb = new StringBuilder();
        for (String w : folded.split("[^\\p{L}\\p{N}]+")) {
            if (w.startsWith("-") || w.contains(":")) continue;
            sb.append(w);
        }
        return sb.toString().toLowerCase();
    }
}
