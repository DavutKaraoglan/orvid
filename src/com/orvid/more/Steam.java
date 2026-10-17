package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Steam extends Site {

    Steam() {
        super(146, "Steam", "store\\.steampowered\\.com/app/(\\d+)");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        JsonArray items = json(circuit, "https://store.steampowered.com/api/storesearch/?l=english&cc=US&term=" + enc(query))
                .getArray("items");
        Source.Page p = new Source.Page();
        for (int i = 0; i < items.size(); i++) {
            JsonObject a = items.getObject(i);
            if (!"app".equals(a.getString("type", null))) continue;
            long id = a.getLong("id", 0);
            p.items.add(item("https://store.steampowered.com/app/" + id + "/", a.getString("name", "") + " - Trailer",
                    "Steam", -1, -1, "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/" + id + "/header.jpg"));
        }
        durations(p.items, DURATION_BUDGET_MS, item -> {
            String dash = trailer(details(circuit, id(item.getUrl()))).getString("dash_h264", null);
            if (dash == null) return -1;
            Matcher m = TOTAL.matcher(text(circuit, dash, null, null));
            return m.find() ? isoSeconds(m.group(1)) : -1;
        });
        return p;
    }

    private static final long DURATION_BUDGET_MS = 4000;
    private static final Pattern TOTAL = Pattern.compile("mediaPresentationDuration=\"([^\"]+)\"");

    private JsonObject details(String circuit, String id) throws IOException {
        JsonObject app = json(circuit, "https://store.steampowered.com/api/appdetails?l=english&appids=" + id).getObject(id);
        if (!app.getBoolean("success", false)) throw new IOException("video unavailable");
        return app.getObject("data");
    }

    private static JsonObject trailer(JsonObject data) throws IOException {
        JsonArray movies = data.getArray("movies");
        JsonObject pick = null;
        for (int i = 0; i < movies.size(); i++) {
            JsonObject m = movies.getObject(i);
            if (pick == null || m.getBoolean("highlight", false) && !pick.getBoolean("highlight", false)) pick = m;
        }
        if (pick == null) throw new IOException("no trailer");
        return pick;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        JsonObject data = details(circuit, id(url));
        JsonObject pick = trailer(data);
        String hls = pick.getString("hls_h264", null);
        String title = data.getString("name", "") + " - " + pick.getString("name", "Trailer");
        String author = data.getArray("developers").isEmpty() ? "Steam" : data.getArray("developers").getString(0);
        if (hls != null) return new Direct(title, author, hls, true, null);
        String mp4 = pick.getObject("mp4").getString("max", pick.getObject("mp4").getString("480", null));
        if (mp4 == null) throw new IOException("no trailer");
        return new Direct(title, author, mp4, false, null);
    }
}
