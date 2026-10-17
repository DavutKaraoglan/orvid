package com.orvid.ext;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.adult.HtmlTube;
import com.orvid.play.Direct;
import com.orvid.search.Filters;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ExtSource extends Source {

    public final String repo;
    public final int version;
    public final boolean webPlayback;
    private final Pattern match;
    private final HtmlTube html;
    private final Script script;

    ExtSource(String repo, String key, String name, int serviceId, double weight, boolean adult, int version,
              Pattern match, boolean webPlayback, HtmlTube html, Script script) {
        super(key, name, 0, serviceId, weight, !adult, adult);
        this.repo = repo;
        this.version = version;
        this.match = match;
        this.webPlayback = webPlayback;
        this.html = html;
        this.script = script;
    }

    public boolean handles(String url) {
        return url != null && match.matcher(url).find();
    }

    @Override
    public boolean usesWebView() {
        return webPlayback;
    }

    @Override
    public Page search(String circuit, String query, Filters f, String next) throws Exception {
        if (html != null) {
            HtmlTube.Page p = html.search(circuit, query, next);
            return new Page(p.items, p.next);
        }
        JsonObject out = script.call(circuit, "search", query, next);
        Page p = new Page();
        JsonArray items = Script.array(out, "items");
        for (int i = 0; i < items.size(); i++) {
            JsonObject o = items.getObject(i);
            String url = o.getString("url", null);
            if (url == null || !handles(url)) continue;
            p.items.add(item(o, url));
        }
        p.next = out.getString("next", null);
        return p;
    }

    private StreamInfoItem item(JsonObject o, String url) {
        StreamInfoItem item = new StreamInfoItem(serviceId, url, o.getString("title", ""), StreamType.VIDEO_STREAM);
        item.setUploaderName(o.getString("author", ""));
        item.setDuration(Math.round(o.getDouble("duration", -1)));
        item.setViewCount(Math.round(o.getDouble("views", -1)));
        String thumb = o.getString("thumb", null);
        List<Image> thumbs = new ArrayList<>();
        if (thumb != null && thumb.startsWith("http")) thumbs.add(new Image(thumb, 180, 320, Image.ResolutionLevel.MEDIUM));
        item.setThumbnails(thumbs);
        List<Image> avatars = new ArrayList<>();
        String avatar = o.getString("avatar", null);
        if (avatar != null && avatar.startsWith("http")) avatars.add(new Image(avatar, 68, 68, Image.ResolutionLevel.LOW));
        item.setUploaderAvatars(avatars);
        Object date = o.get("date");
        if (date instanceof Number) com.orvid.search.Dates.set(item, ((Number) date).longValue());
        else if (date instanceof String) com.orvid.search.Dates.set(item, (String) date);
        return item;
    }

    public Direct resolve(String circuit, String url) throws IOException {
        if (html != null) {
            HtmlTube.Media m = html.resolve(circuit, url);
            return new Direct(m.title, "", m.mp4, m.mp4.contains(".m3u8"), m.referer);
        }
        JsonObject out = script.call(circuit, "resolve", url, null);
        String media = out.getString("url", null);
        if (media == null) throw new IOException("video unavailable");
        return new Direct(out.getString("title", ""), out.getString("author", ""), media,
                out.getBoolean("hls", media.contains(".m3u8")), out.getString("referer", null));
    }
}
