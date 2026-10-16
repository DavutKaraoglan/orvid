package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;

final class NineGag extends Site {

    NineGag() {
        super(144, "9GAG", "9gag\\.com/gag/([A-Za-z0-9]+)");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        JsonObject data = json(circuit, "https://9gag.com/v1/search-posts?" + (next != null ? next : "query=" + enc(query)))
                .getObject("data");
        JsonArray posts = data.getArray("posts");
        Source.Page p = new Source.Page();
        for (int i = 0; i < posts.size(); i++) {
            JsonObject post = posts.getObject(i);
            JsonObject video = post.getObject("images").getObject("image460sv");
            if (video.isEmpty() || post.getInt("nsfw", 0) != 0) continue;
            String id = post.getString("id", null);
            if (id == null) continue;
            JsonObject thumb = post.getObject("images").getObject("image460");
            StreamInfoItem item = item("https://9gag.com/gag/" + id, post.getString("title", ""),
                    post.getObject("postSection").getString("name", "9GAG"), video.getLong("duration", -1),
                    -1, thumb.getString("webpUrl", thumb.getString("url", null)));
            com.orvid.search.Dates.set(item, post.getLong("creationTs", 0));
            p.items.add(item);
        }
        String cursor = data.getString("nextCursor", null);
        if (cursor != null && !posts.isEmpty()) p.next = cursor;
        return p;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        JsonObject post = json(circuit, "https://9gag.com/v1/post?id=" + id(url)).getObject("data").getObject("post");
        JsonObject images = post.getObject("images");
        String src = images.getObject("image460sv").getString("url", null);
        if (src == null) throw new IOException("video unavailable");
        return new Direct(post.getString("title", ""), post.getObject("postSection").getString("name", ""), src, false, null);
    }
}
