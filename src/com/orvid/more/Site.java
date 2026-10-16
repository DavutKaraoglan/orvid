package com.orvid.more;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.orvid.net.Diag;
import com.orvid.net.Fetch;
import com.orvid.play.Direct;
import com.orvid.search.Source;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Site {

    public final int serviceId;
    public final String name;
    private final Pattern url;

    protected Site(int serviceId, String name, String urlPattern) {
        this.serviceId = serviceId;
        this.name = name;
        this.url = Pattern.compile(urlPattern);
    }

    public abstract Source.Page search(String circuit, String query, String next) throws IOException;

    public abstract Direct resolve(String circuit, String url) throws IOException;

    public boolean handles(String url) {
        return id(url) != null;
    }

    protected String id(String pageUrl) {
        if (pageUrl == null) return null;
        Matcher m = url.matcher(pageUrl);
        return m.find() ? m.group(1) : null;
    }

    protected StreamInfoItem item(String url, String title, String author, long seconds, long views, String thumb) {
        StreamInfoItem item = new StreamInfoItem(serviceId, url, clean(title), StreamType.VIDEO_STREAM);
        item.setUploaderName(author == null ? "" : clean(author));
        item.setDuration(seconds);
        item.setViewCount(views);
        if (thumb != null && !thumb.isEmpty()) {
            List<Image> thumbs = new ArrayList<>();
            thumbs.add(new Image(https(thumb), 180, 320, Image.ResolutionLevel.MEDIUM));
            item.setThumbnails(thumbs);
        }
        return item;
    }



    protected static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    protected static String https(String url) {
        return url != null && url.startsWith("http://") ? "https://" + url.substring(7) : url;
    }

    protected static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#039;", "'").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").trim();
    }

    protected static long clock(String s) {
        if (s == null || s.isEmpty()) return -1;
        long total = 0;
        try {
            for (String part : s.trim().split(":")) total = total * 60 + Long.parseLong(part.trim());
            return total;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    protected static long number(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v == null) return -1;
        try {
            return (long) Double.parseDouble(v.toString().replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    protected JsonObject json(String circuit, String url) throws IOException {
        return parse(text(circuit, url, null, null));
    }

    protected JsonArray array(String circuit, String url) throws IOException {
        String body = text(circuit, url, null, null);
        try {
            return JsonParser.array().from(body);
        } catch (Exception e) {
            throw new IOException(name + " answer is not JSON");
        }
    }

    protected JsonObject post(String circuit, String url, String type, String body) throws IOException {
        return parse(text(circuit, url, type, body));
    }

    private JsonObject parse(String body) throws IOException {
        try {
            return JsonParser.object().from(body);
        } catch (Exception e) {
            throw new IOException(name + " answer is not JSON");
        }
    }

    protected String text(String circuit, String url, String type, String body, String... headers) throws IOException {
        long began = System.currentTimeMillis();
        String result;
        try {
            result = Fetch.on(circuit, () -> {
                HttpURLConnection c = Fetch.open(url);
                c.setRequestProperty("User-Agent", Fetch.USER_AGENT);
                c.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                for (int i = 0; i + 1 < headers.length; i += 2) c.setRequestProperty(headers[i], headers[i + 1]);
                if (body != null) {
                    c.setRequestMethod("POST");
                    c.setRequestProperty("Content-Type", type);
                    c.setDoOutput(true);
                    try (OutputStream out = c.getOutputStream()) {
                        out.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                }
                int code = c.getResponseCode();
                if (code == 403 || code == 429) throw new IOException("Captcha needed (HTTP " + code + ")");
                if (code >= 400) throw new IOException("HTTP " + code);
                try (InputStream in = c.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            });
        } catch (IOException e) {
            Diag.mark(name + " FAIL " + e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
        Diag.mark(name + " " + result.length() / 1024 + "KB " + (System.currentTimeMillis() - began) + "ms");
        return result;
    }
}
