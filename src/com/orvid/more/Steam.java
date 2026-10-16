package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import java.io.IOException;

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
        return p;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        String id = id(url);
        JsonObject app = json(circuit, "https://store.steampowered.com/api/appdetails?l=english&appids=" + id).getObject(id);
        if (!app.getBoolean("success", false)) throw new IOException("video unavailable");
        JsonObject data = app.getObject("data");
        JsonArray movies = data.getArray("movies");
        JsonObject pick = null;
        for (int i = 0; i < movies.size(); i++) {
            JsonObject m = movies.getObject(i);
            if (pick == null || m.getBoolean("highlight", false) && !pick.getBoolean("highlight", false)) pick = m;
        }
        if (pick == null) throw new IOException("no trailer");
        String hls = pick.getString("hls_h264", null);
        String title = data.getString("name", "") + " - " + pick.getString("name", "Trailer");
        String author = data.getArray("developers").isEmpty() ? "Steam" : data.getArray("developers").getString(0);
        if (hls != null) return new Direct(title, author, hls, true, null);
        String mp4 = pick.getObject("mp4").getString("max", pick.getObject("mp4").getString("480", null));
        if (mp4 == null) throw new IOException("no trailer");
        return new Direct(title, author, mp4, false, null);
    }
}
