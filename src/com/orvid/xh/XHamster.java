package com.orvid.xh;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class XHamster {

    public static final int SERVICE_ID = 109;
    private static final String SITE = "https://xhamster.com";
    public static final String REFERER = SITE + "/";
    private static final Pattern VIDEO = Pattern.compile("xhamster\\d*\\.(?:com|one|desi)/videos/[A-Za-z0-9-]+");
    private static final Pattern INITIALS = Pattern.compile("window\\.initials\\s*=\\s*\\{");
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{12,}");
    private static final Pattern HEX_PATH = Pattern.compile("^/([0-9a-fA-F]{12,})([/,].+)$");

    private XHamster() {
    }

    public static final class Page {
        public final List<StreamInfoItem> items = new ArrayList<>();
        public String next;
    }

    public static final class Media {
        public String title = "";
        public String author = "";
        public String hls;
        public String referer = REFERER;
    }

    public static boolean handles(String url) {
        return url != null && VIDEO.matcher(url).find();
    }

    public static Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 1 : Integer.parseInt(next);
        String url = SITE + "/search/" + URLEncoder.encode(query, "UTF-8").replace("+", "%20")
                + (page > 1 ? "?page=" + page : "");
        JsonObject initials = initials(com.orvid.adult.Web.get(circuit, url, "xh search", null));
        JsonObject result = initials.getObject("searchResult");
        JsonArray thumbs = result.getArray("videoThumbProps");
        Page p = new Page();
        for (int i = 0; i < thumbs.size(); i++) {
            JsonObject v = thumbs.getObject(i);
            String link = v.getString("pageURL", null);
            if (!handles(link)) continue;
            StreamInfoItem item = new StreamInfoItem(SERVICE_ID, link, v.getString("title", ""), StreamType.VIDEO_STREAM);
            JsonObject landing = v.getObject("landing");
            item.setUploaderName(landing.getString("name", ""));
            item.setDuration(v.getLong("duration", -1));
            item.setViewCount(v.getLong("views", -1));
            long created = v.getLong("created", 0);
            if (created > 0) item.setUploadDate(new DateWrapper(Instant.ofEpochSecond(created), false));
            List<Image> images = new ArrayList<>();
            String thumb = v.getString("thumbURL", v.getString("imageURL", null));
            if (thumb != null) images.add(new Image(thumb, 360, 640, Image.ResolutionLevel.MEDIUM));
            item.setThumbnails(images);
            p.items.add(item);
        }
        if (!p.items.isEmpty()) {
            JsonObject pagination = result.getObject("pagination");
            int max = pagination.getInt("maxPage", pagination.getInt("pageCount", page + 1));
            if (page < max) p.next = Integer.toString(page + 1);
        }
        return p;
    }

    public static Media resolve(String circuit, String url) throws IOException {
        if (!handles(url)) throw new IOException("not a video link");
        String html = com.orvid.adult.Web.get(circuit, url, "xh video", null);
        JsonObject initials = initials(html);
        JsonObject sources = initials.getObject("xplayerSettings").getObject("sources");
        JsonObject hls = sources.getObject("hls");
        Media m = new Media();
        m.referer = url;
        for (String key : new String[] {"url", "fallback"}) {
            if (m.hls == null) m.hls = decipherUrl(hls.getString(key, null));
        }
        for (String codec : new String[] {"h264", "av1"}) {
            JsonObject c = hls.getObject(codec);
            for (String key : new String[] {"url", "fallback"}) {
                if (m.hls == null) m.hls = decipherUrl(c.getString(key, null));
            }
        }
        if (m.hls == null) throw new IOException("no stream");
        JsonObject model = initials.getObject("videoModel");
        m.title = model.getString("title", "");
        if (m.title.isEmpty()) {
            Matcher t = Pattern.compile("<title>([^<]*)</title>").matcher(html);
            if (t.find()) m.title = t.group(1).replaceAll("\\s*[|-]\\s*xHamster.*$", "").trim();
        }
        m.author = model.getObject("author").getString("name", "");
        return m;
    }

    static String decipherUrl(String value) {
        if (value == null || value.isEmpty()) return null;
        if (HEX.matcher(value).matches()) return decipher(value);
        if (!value.startsWith("http")) return null;
        URI u = URI.create(value);
        Matcher m = HEX_PATH.matcher(u.getRawPath());
        if (!m.matches()) return value;
        String plain = decipher(m.group(1));
        if (plain == null) return null;
        return u.getScheme() + "://" + u.getRawAuthority() + "/" + plain + m.group(2)
                + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
    }

    static String decipher(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) data[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        if (data.length < 6) return null;
        int algo = data[0] & 0xFF;
        int seed = (data[1] & 0xFF) | (data[2] & 0xFF) << 8 | (data[3] & 0xFF) << 16 | (data[4] & 0xFF) << 24;
        Bytes gen = new Bytes(algo, seed);
        if (!gen.known()) return null;
        StringBuilder out = new StringBuilder(data.length - 5);
        for (int i = 5; i < data.length; i++) out.append((char) (((data[i] & 0xFF) ^ gen.next()) & 0xFF));
        return out.toString();
    }

    static final class Bytes {
        private final int algo;
        private int s;

        Bytes(int algo, int seed) {
            this.algo = algo;
            this.s = seed;
        }

        boolean known() {
            return algo >= 1 && algo <= 7;
        }

        int next() {
            int r;
            switch (algo) {
                case 1:
                    s = s * 1664525 + 1013904223;
                    r = s;
                    break;
                case 2: {
                    int x = s;
                    x ^= x << 13;
                    x ^= x >>> 17;
                    x ^= x << 5;
                    s = x;
                    r = x;
                    break;
                }
                case 3: {
                    s = s + 0x9e3779b9;
                    int x = s;
                    x ^= x >>> 16;
                    x *= 0x85ebca77;
                    x ^= x >>> 13;
                    x *= 0xc2b2ae3d;
                    r = x ^ (x >>> 16);
                    break;
                }
                case 4: {
                    s = s + 0x6d2b79f5;
                    int x = s;
                    x = (x << 7) | (x >>> 25);
                    x += 0x9e3779b9;
                    x ^= x >>> 11;
                    r = x * 0x27d4eb2d;
                    break;
                }
                case 5: {
                    int x = s;
                    x ^= x << 7;
                    x ^= x >>> 9;
                    x ^= x << 8;
                    x += 0xa5a5a5a5;
                    s = x;
                    r = x;
                    break;
                }
                case 6: {
                    s = s * 0x2c9277b5 + 0xac564b05;
                    int x = s ^ (s >>> 18);
                    int shift = (s >>> 27) & 31;
                    r = x >>> shift;
                    break;
                }
                default: {
                    s = s + 0x9e3779b9;
                    int e = s ^ (s << 5);
                    e *= 0x7feb352d;
                    e ^= e >>> 15;
                    r = e * 0x846ca68b;
                    break;
                }
            }
            return r & 0xFF;
        }
    }

    static JsonObject initials(String html) throws IOException {
        Matcher m = INITIALS.matcher(html);
        if (!m.find()) throw new IOException("Captcha needed (xhamster page without data)");
        String json = com.orvid.ay.Aylo.object(html, m.end() - 1);
        try {
            return JsonParser.object().from(json);
        } catch (Exception e) {
            throw new IOException("xhamster data is not JSON");
        }
    }

}
