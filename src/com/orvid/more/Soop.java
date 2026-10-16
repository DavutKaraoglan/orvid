package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;

final class Soop extends Site {

    private static final int PAGE = 20;

    Soop() {
        super(148, "SOOP", "vod\\.(?:sooplive\\.co\\.kr|afreecatv\\.com)/player/(\\d+)");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 1 : Integer.parseInt(next);
        JsonObject root = json(circuit, "https://sch.sooplive.co.kr/api.php?m=vodSearch&v=2.0&szOrder=score&szKeyword="
                + enc(query) + "&nPageNo=" + page + "&nListCnt=" + PAGE);
        JsonArray data = root.getArray("DATA");
        Source.Page p = new Source.Page();
        for (int i = 0; i < data.size(); i++) {
            JsonObject v = data.getObject(i);
            if (!"OPEN_ALL".equals(v.getString("auth", "OPEN_ALL")) || "Y".equals(v.getString("ppv_yn", "N"))
                    || v.getInt("grade", 0) >= 19) continue;
            String no = v.getString("title_no", null);
            if (no == null) continue;
            StreamInfoItem item = item("https://vod.sooplive.co.kr/player/" + no, v.getString("title", ""),
                    v.getString("user_nick", ""), clock(v.getString("duration", null)), number(v.get("view_cnt")),
                    v.getString("thumbnail_path", null));
            com.orvid.search.Dates.set(item, number(v.get("timestamp")));
            p.items.add(item);
        }
        if (root.getBoolean("HAS_MORE_LIST", false)) p.next = Integer.toString(page + 1);
        return p;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        JsonObject root = post(circuit, "https://api.m.sooplive.co.kr/station/video/a/view",
                "application/x-www-form-urlencoded", "nTitleNo=" + id(url) + "&nApiLevel=10&nPlaylistIdx=0");
        if (root.getInt("result", 0) != 1) throw new IOException("video unavailable");
        JsonObject data = root.getObject("data");
        JsonArray files = data.getArray("files");
        if (files.isEmpty()) throw new IOException("no stream");
        String file = files.getObject(0).getString("file", null);
        if (file == null) throw new IOException("no stream");
        return new Direct(clean(data.getString("full_title", data.getString("title", ""))),
                data.getString("writer_nick", ""), file, file.contains(".m3u8"), null);
    }
}
