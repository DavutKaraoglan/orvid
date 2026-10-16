package com.orvid.adult;

import com.orvid.net.Fetch;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HtmlTube {

    public static final class Page {
        public final List<StreamInfoItem> items = new ArrayList<>();
        public String next;
    }

    public static final class Media {
        public String title = "";
        public String mp4;
        public String referer;
    }

    public final int serviceId;
    private final String site;
    private final String firstPage;
    private final String laterPage;
    private final Pattern videoUrl;
    private final String cardMarker;
    private final Pattern href;
    private final Pattern title;
    private final Pattern thumb;
    private final Pattern duration;
    private final Pattern views;
    private Pattern iframe;
    private Pattern mustContain;
    private Pattern mustSkip;
    public boolean web;
    private boolean paged;

    public HtmlTube paged() {
        paged = true;
        return this;
    }

    public HtmlTube web() {
        web = true;
        return this;
    }

    public HtmlTube skip(String pattern) {
        mustSkip = Pattern.compile(pattern);
        return this;
    }

    private String linkPrefix = "";

    public HtmlTube linkPrefix(String value) {
        linkPrefix = value;
        return this;
    }
    private String referer;

    public HtmlTube iframe(String pattern) {
        iframe = Pattern.compile(pattern);
        return this;
    }

    public HtmlTube only(String pattern) {
        mustContain = Pattern.compile(pattern);
        return this;
    }

    public HtmlTube referer(String value) {
        referer = value;
        return this;
    }

    private static final Pattern SOURCE = Pattern.compile("<source\\b[^>]*>");
    private static final Pattern KVS = Pattern.compile("\\b(video_(?:alt_)?url\\d*)\\s*:\\s*'([^']+)'");
    private static final Pattern STREAM_MARKER = Pattern.compile(
            "<source\\b[^>]*(?:type=[\"']video|\\.mp4|\\.m3u8)|video_(?:alt_)?url\\d*\\s*:\\s*'");
    private static final Pattern LICENSE = Pattern.compile("license_code\\s*:\\s*'([^']+)'");
    private static final Pattern SRC = Pattern.compile("\\bsrc=[\"']([^\"']+)[\"']");
    private static final Pattern LABEL = Pattern.compile("\\b(?:title|label|res|size)=[\"']([^\"']+)[\"']");
    private static final Pattern OG_TITLE = Pattern.compile("property=\"og:title\"\\s+content=\"([^\"]*)\"");
    private static final Pattern H1 = Pattern.compile("<h1[^>]*>\\s*([^<]+)<");

    public HtmlTube(int serviceId, String site, String firstPage, String laterPage, String videoUrl, String cardMarker,
                    String href, String title, String thumb, String duration, String views) {
        this.serviceId = serviceId;
        this.site = site;
        this.firstPage = firstPage;
        this.laterPage = laterPage;
        this.videoUrl = Pattern.compile(videoUrl);
        this.cardMarker = cardMarker;
        this.href = Pattern.compile(href);
        this.title = Pattern.compile(title);
        this.thumb = Pattern.compile(thumb);
        this.duration = duration == null ? null : Pattern.compile(duration);
        this.views = views == null ? null : Pattern.compile(views);
    }

    public boolean handles(String url) {
        return url != null && videoUrl.matcher(url).find();
    }

    public Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 1 : Integer.parseInt(next);
        String q = URLEncoder.encode(query, "UTF-8");
        String qPath = q.replace("+", "-");
        String url = (page > 1 ? laterPage : firstPage).replace("{q}", q).replace("{qp}", qPath)
                .replace("{p}", Integer.toString(page));
        String html = Web.get(circuit, url, "tube search " + URI.create(site).getHost(), null);
        Page p = new Page();
        Set<String> seen = new HashSet<>();
        String[] cards = html.split(Pattern.quote(cardMarker));
        for (int i = 1; i < cards.length; i++) {
            String b = cards[i];
            if (mustContain != null && !mustContain.matcher(b).find()) continue;
            if (mustSkip != null && mustSkip.matcher(b).find()) continue;
            Matcher h = href.matcher(b);
            if (!h.find()) continue;
            String link = absolute(linkPrefix + h.group(1));
            if (!seen.add(link) || !handles(link)) continue;
            Matcher t = title.matcher(b);
            if (!t.find()) continue;
            StreamInfoItem item = new StreamInfoItem(serviceId, link, Web.unescape(t.group(1)), StreamType.VIDEO_STREAM);
            item.setUploaderName("");
            long secs = -1;
            if (duration != null) {
                Matcher d = duration.matcher(b);
                if (d.find()) secs = Web.duration(d.group(1));
            }
            item.setDuration(secs);
            long v = -1;
            if (views != null) {
                Matcher m = views.matcher(b);
                if (m.find()) v = Web.number(m.group(1));
            }
            item.setViewCount(v);
            List<Image> thumbs = new ArrayList<>();
            Matcher th = thumb.matcher(b);
            if (th.find()) thumbs.add(new Image(absolute(th.group(1)), 180, 320, Image.ResolutionLevel.MEDIUM));
            item.setThumbnails(thumbs);
            p.items.add(item);
        }
        String nextUrl = laterPage.replace("{q}", q).replace("{qp}", qPath).replace("{p}", Integer.toString(page + 1));
        String nextPath = nextUrl.startsWith(site) ? nextUrl.substring(site.length()) : nextUrl;
        if (!p.items.isEmpty() && (paged || html.contains(nextPath) || html.contains(nextUrl)
                || html.contains(nextPath.replace("&", "&amp;")))) {
            p.next = Integer.toString(page + 1);
        }
        return p;
    }

    public Media resolve(String circuit, String url) throws IOException {
        if (!handles(url)) throw new IOException("not a video link");
        String html = Web.getUntil(circuit, url, "tube video " + URI.create(site).getHost(), null,
                iframe != null ? iframe : STREAM_MARKER, iframe != null ? 2048 : 24 * 1024);
        Media m = new Media();
        m.referer = referer != null ? referer : url;
        Matcher t = OG_TITLE.matcher(html);
        if (t.find()) m.title = trimSiteName(Web.unescape(t.group(1)));
        else {
            Matcher h = H1.matcher(html);
            if (h.find()) m.title = Web.unescape(h.group(1));
        }
        if (iframe != null) {
            Matcher f = iframe.matcher(html);
            if (!f.find()) throw new IOException("no player");
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put("Referer", url);
            html = Web.get(circuit, absolute(f.group(1)), "tube player " + URI.create(site).getHost(), headers)
                    .replace("\\\"", "\"").replace("\\/", "/");
        }
        int cap = Fetch.torMode() ? 720 : 1080;
        int best = -1;
        String lowest = null;
        int lowestQ = Integer.MAX_VALUE;
        Matcher s = SOURCE.matcher(html);
        while (s.find()) {
            String tag = s.group();
            Matcher src = SRC.matcher(tag);
            if (!src.find()) continue;
            int q = quality(tag);
            if (q <= 0) q = 480;
            String u = absolute(Web.unescape(src.group(1)));
            if (q <= cap && q > best) {
                best = q;
                m.mp4 = u;
            }
            if (q < lowestQ) {
                lowestQ = q;
                lowest = u;
            }
        }
        Matcher lic = LICENSE.matcher(html);
        String license = lic.find() ? lic.group(1) : null;
        Matcher kvs = KVS.matcher(html);
        while (kvs.find()) {
            String u = kvs.group(2);
            if (u.startsWith("function/")) {
                u = license == null ? null : kvsReal(u, license);
                if (u == null) continue;
            }
            Matcher label = Pattern.compile(Pattern.quote(kvs.group(1)) + "_text\\s*:\\s*'([^']*)'").matcher(html);
            int q = 480;
            if (label.find()) {
                Matcher n = Pattern.compile("(\\d{3,4})").matcher(label.group(1));
                if (n.find()) q = Integer.parseInt(n.group(1));
            }
            u = absolute(u);
            if (q <= cap && q > best) {
                best = q;
                m.mp4 = u;
            }
            if (q < lowestQ) {
                lowestQ = q;
                lowest = u;
            }
        }
        if (m.mp4 == null) m.mp4 = lowest;
        if (m.mp4 == null) throw new IOException("no stream");
        return m;
    }

    private String trimSiteName(String title) {
        String name = URI.create(site).getHost().replaceFirst("^www\\.", "");
        String label = name.substring(0, name.indexOf('.')).toLowerCase();
        String[] parts = title.split("\\s+[|\\u2013\\u2014-]\\s+");
        if (parts.length < 2) return title;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String low = parts[i].toLowerCase();
            boolean edge = i == 0 || i == parts.length - 1;
            if (edge && (low.contains(label) || low.equals("free porn videos") || low.equals("porn video"))) continue;
            if (out.length() > 0) out.append(" - ");
            out.append(parts[i]);
        }
        return out.length() == 0 ? title : out.toString();
    }

    static String kvsReal(String videoUrl, String license) {
        if (!videoUrl.startsWith("function/0/")) return null;
        String rest = videoUrl.substring("function/0/".length());
        int scheme = rest.indexOf("://");
        if (scheme < 0) return null;
        int pathStart = rest.indexOf('/', scheme + 3);
        int query = rest.indexOf('?', pathStart);
        String head = rest.substring(0, pathStart);
        String path = query < 0 ? rest.substring(pathStart) : rest.substring(pathStart, query);
        String tail = query < 0 ? "" : rest.substring(query);
        String[] parts = path.split("/", -1);
        if (parts.length < 4 || parts[3].length() < 32) return null;
        int[] token = kvsToken(license);
        if (token == null || token.length < 32) return null;
        String hash = parts[3].substring(0, 32);
        int[] idx = new int[32];
        for (int i = 0; i < 32; i++) idx[i] = i;
        int accum = 0;
        for (int src = 31; src >= 0; src--) {
            accum += token[src];
            int dest = (src + accum) % 32;
            int t = idx[src];
            idx[src] = idx[dest];
            idx[dest] = t;
        }
        StringBuilder h = new StringBuilder();
        for (int i : idx) h.append(hash.charAt(i));
        parts[3] = h + parts[3].substring(32);
        return head + String.join("/", parts) + tail;
    }

    private static int[] kvsToken(String license) {
        String l = license.replace("$", "");
        if (!l.matches("\\d+")) return null;
        String mod = l.replace('0', '1');
        int center = mod.length() / 2;
        long front = Long.parseLong(mod.substring(0, center + 1));
        long back = Long.parseLong(mod.substring(center));
        String m = Long.toString(4 * Math.abs(front - back));
        if (m.length() > center + 1) m = m.substring(0, center + 1);
        int[] out = new int[m.length() * 4];
        int k = 0;
        for (int i = 0; i < m.length(); i++) {
            int cur = m.charAt(i) - '0';
            for (int off = 0; off < 4; off++) {
                if (i + off >= l.length()) return null;
                out[k++] = (l.charAt(i + off) - '0' + cur) % 10;
            }
        }
        return out;
    }

    private static int quality(String tag) {
        Matcher l = LABEL.matcher(tag);
        while (l.find()) {
            String v = l.group(1).trim().toUpperCase();
            Matcher n = Pattern.compile("(\\d{3,4})P?").matcher(v);
            if (n.find()) return Integer.parseInt(n.group(1));
            if (v.equals("HQ") || v.equals("HD")) return 720;
            if (v.equals("LQ") || v.equals("SD")) return 360;
        }
        return 0;
    }

    private String absolute(String u) {
        if (u.startsWith("//")) return "https:" + u;
        if (u.startsWith("/")) return site + u;
        return u;
    }
}
