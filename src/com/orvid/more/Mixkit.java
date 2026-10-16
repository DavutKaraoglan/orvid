package com.orvid.more;

import com.orvid.play.Direct;
import com.orvid.search.Source;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Mixkit extends Site {

    private static final Pattern CARD = Pattern.compile("<img src=\"https://assets\\.mixkit\\.co/videos/(\\d+)/\\1-thumb-[^\"]+\"[^>]*alt=\"([^\"]*)\".*?"
            + "href=\"(/free-stock-video/[a-z0-9-]+-\\1/)\"", Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("<h1[^>]*>\\s*([^<]+?)\\s*</h1>");

    Mixkit() {
        super(151, "Mixkit", "mixkit\\.co/free-stock-video/[a-z0-9-]+-(\\d+)/");
    }

    @Override
    public Source.Page search(String circuit, String query, String next) throws IOException {
        String slug = query.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.isEmpty()) return new Source.Page();
        int page = next == null ? 1 : Integer.parseInt(next);
        String html;
        try {
            html = text(circuit, "https://mixkit.co/free-stock-video/" + slug + "/" + (page > 1 ? "?page=" + page : ""), null, null);
        } catch (IOException e) {
            if (!e.getMessage().contains("404") || !slug.contains("-")) throw e;
            slug = slug.substring(0, slug.indexOf('-'));
            html = text(circuit, "https://mixkit.co/free-stock-video/" + slug + "/" + (page > 1 ? "?page=" + page : ""), null, null);
        }
        Source.Page p = new Source.Page();
        Matcher m = CARD.matcher(html);
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (m.find()) {
            if (!seen.add(m.group(1))) continue;
            p.items.add(item("https://mixkit.co" + m.group(3), m.group(2), "Mixkit", -1, -1,
                    "https://assets.mixkit.co/videos/" + m.group(1) + "/" + m.group(1) + "-thumb-360-0.jpg"));
        }
        if (p.items.size() >= 20 && html.contains("page=" + (page + 1))) p.next = Integer.toString(page + 1);
        return p;
    }

    @Override
    public Direct resolve(String circuit, String url) throws IOException {
        String id = id(url);
        String title = "";
        try {
            Matcher m = TITLE.matcher(text(circuit, url, null, null));
            if (m.find()) title = clean(m.group(1));
        } catch (IOException ignored) {
        }
        return new Direct(title, "Mixkit", "https://assets.mixkit.co/videos/" + id + "/" + id + "-720.mp4", false, null);
    }
}
