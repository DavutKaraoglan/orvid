package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Chzzk extends Site {

    private static final int PAGE = 20;
    private static final Pattern REP = Pattern.compile("<Representation [^>]*?height=\"(\\d+)\"[^>]*?mimeType=\"video/mp4\"[^>]*codecs=\"[^\"]*mp4a[^>]*>.*?<BaseURL>([^<]+)</BaseURL>", Pattern.DOTALL);

    Chzzk() {
        super(147, "Chzzk", "chzzk\\.naver\\.com/video/(\\d+)");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        int offset = next == null ? 0 : Integer.parseInt(next);
        JsonObject content = json(circuit, "https://api.chzzk.naver.com/service/v1/search/videos?keyword=" + enc(query)
                + "&offset=" + offset + "&size=" + PAGE).getObject("content");
        JsonArray data = content.getArray("data");
        Source.Page p = new Source.Page();
        for (int i = 0; i < data.size(); i++) {
            JsonObject row = data.getObject(i);
            JsonObject v = row.getObject("video");
            if (v.getBoolean("adult", false) || v.get("paidProductId") != null) continue;
            long no = v.getLong("videoNo", 0);
            if (no == 0) continue;
            StreamInfoItem item = item("https://chzzk.naver.com/video/" + no, v.getString("videoTitle", ""),
                    row.getObject("channel").getString("channelName", ""), v.getLong("duration", -1),
                    v.getLong("readCount", -1), small(v.getString("thumbnailImageUrl", null)));
            com.orvid.search.Dates.set(item, v.getLong("publishDateAt", 0) / 1000);
            p.items.add(item);
        }
        if (!content.getObject("page").getObject("next").isEmpty()) p.next = Integer.toString(offset + PAGE);
        return p;
    }

    private static String small(String url) {
        return url == null || url.contains("?") ? url : url + "?type=w320";
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        JsonObject c = json(circuit, "https://api.chzzk.naver.com/service/v3/videos/" + id(url)).getObject("content");
        String videoId = c.getString("videoId", null);
        String key = c.getString("inKey", null);
        String title = c.getString("videoTitle", "");
        String author = c.getObject("channel").getString("channelName", "");
        if (videoId == null || key == null) {
            String rewind = c.getString("liveRewindPlaybackJson", null);
            Matcher m = rewind == null ? null : Pattern.compile("\"path\"\\s*:\\s*\"([^\"]+\\.m3u8[^\"]*)\"").matcher(rewind);
            if (m != null && m.find()) return new Direct(title, author, m.group(1), true, null);
            throw new IOException("video unavailable");
        }
        String mpd = text(circuit, "https://apis.naver.com/neonplayer/vodplay/v1/playback/" + videoId + "?key=" + key, null, null,
                "Accept", "application/dash+xml");
        Matcher m = REP.matcher(mpd);
        String best = null;
        int bestHeight = -1;
        while (m.find()) {
            int h = Integer.parseInt(m.group(1));
            if (h <= 720 && h > bestHeight) {
                bestHeight = h;
                best = m.group(2).replace("&amp;", "&");
            }
        }
        if (best == null) throw new IOException("no stream");
        return new Direct(title, author, best, false, null);
    }
}
