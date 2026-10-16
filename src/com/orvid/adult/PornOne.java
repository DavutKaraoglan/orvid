package com.orvid.adult;

import com.orvid.net.Fetch;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PornOne {

    public static final int SERVICE_ID = 113;
    private static final String SITE = "https://pornone.com";
    private static final Pattern VIDEO = Pattern.compile("https://pornone\\.com/[^\\s\"?#]+/\\d+/?");
    private static final Pattern CARD = Pattern.compile("<a href=\"(https://pornone\\.com/[^\"]+/\\d+/)\"[^>]*videocard[^>]*>");
    private static final Pattern THUMB = Pattern.compile("<img src=\"(https://[^\"]+)\"\\s+alt=\"([^\"]*)\"[^>]*imgvideo");
    private static final Pattern TITLE = Pattern.compile("class=\"videotitle[^\"]*\">\\s*([^<]+)<");
    private static final Pattern DURATION = Pattern.compile("durlabel\">(?:\\s*<img[^>]*>)?\\s*([0-9:]+)");
    private static final Pattern SOURCE = Pattern.compile("<source[^>]+src=\"([^\"]+)\"[^>]*res=\"(\\d+)\"");
    private static final Pattern OG_TITLE = Pattern.compile("property=\"og:title\"\\s+content=\"([^\"]*)\"");

    private PornOne() {
    }

    public static final class Page {
        public final List<StreamInfoItem> items = new ArrayList<>();
        public String next;
    }

    public static final class Media {
        public String title = "";
        public String mp4;
    }

    public static boolean handles(String url) {
        return url != null && VIDEO.matcher(url).find();
    }

    public static Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 1 : Integer.parseInt(next);
        String q = URLEncoder.encode(query, "UTF-8");
        String url = page > 1 ? SITE + "/search/" + page + "?q=" + q : SITE + "/search?q=" + q;
        String html = Web.get(circuit, url, "pornone search", null);
        Page p = new Page();
        Set<String> seen = new HashSet<>();
        Matcher card = CARD.matcher(html);
        List<int[]> spans = new ArrayList<>();
        List<String> links = new ArrayList<>();
        while (card.find()) {
            spans.add(new int[] {card.start(), 0});
            links.add(card.group(1));
        }
        for (int i = 0; i < spans.size(); i++) {
            int end = i + 1 < spans.size() ? spans.get(i + 1)[0] : Math.min(html.length(), spans.get(i)[0] + 6000);
            String b = html.substring(spans.get(i)[0], end);
            String link = links.get(i);
            if (!seen.add(link)) continue;
            Matcher thumb = THUMB.matcher(b);
            Matcher title = TITLE.matcher(b);
            String name = title.find() ? title.group(1) : null;
            String img = null;
            if (thumb.find()) {
                img = thumb.group(1);
                if (name == null) name = thumb.group(2);
            }
            if (name == null) continue;
            StreamInfoItem item = new StreamInfoItem(SERVICE_ID, link, Web.unescape(name), StreamType.VIDEO_STREAM);
            item.setUploaderName("");
            Matcher duration = DURATION.matcher(b);
            item.setDuration(duration.find() ? Web.clock(duration.group(1)) : -1);
            item.setViewCount(-1);
            List<Image> thumbs = new ArrayList<>();
            if (img != null) thumbs.add(new Image(img, 158, 270, Image.ResolutionLevel.MEDIUM));
            item.setThumbnails(thumbs);
            p.items.add(item);
        }
        if (!p.items.isEmpty() && html.contains("/search/" + (page + 1) + "?q=")) p.next = Integer.toString(page + 1);
        return p;
    }

    public static Media resolve(String circuit, String url) throws IOException {
        if (!handles(url)) throw new IOException("not a video link");
        String html = Web.get(circuit, url, "pornone video", null);
        Media m = new Media();
        Matcher t = OG_TITLE.matcher(html);
        if (t.find()) m.title = Web.unescape(t.group(1));
        int cap = Fetch.torMode() ? 720 : 1080;
        int best = -1;
        int lowest = Integer.MAX_VALUE;
        String fallback = null;
        Matcher s = SOURCE.matcher(html);
        while (s.find()) {
            int h = Integer.parseInt(s.group(2));
            if (h <= cap && h > best) {
                best = h;
                m.mp4 = s.group(1);
            }
            if (h < lowest) {
                lowest = h;
                fallback = s.group(1);
            }
        }
        if (m.mp4 == null) m.mp4 = fallback;
        if (m.mp4 == null) throw new IOException("no stream");
        return m;
    }
}
