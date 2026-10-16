package com.orvid.tn;

import com.orvid.net.Fetch;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Tnaflix {

    public static final int SERVICE_ID = 110;
    private static final String SITE = "https://www.tnaflix.com";
    private static final Pattern VIDEO = Pattern.compile("tnaflix\\.com/[^\\s\"]+/video\\d+");
    private static final Pattern HREF = Pattern.compile("href=\"(https://www\\.tnaflix\\.com/[^\"]+/video\\d+)\"");
    private static final Pattern TITLE = Pattern.compile("class=\"video-title[^\"]*\">([^<]+)<");
    private static final Pattern DURATION = Pattern.compile("video-duration\">\\s*([0-9:]+)");
    private static final Pattern THUMB = Pattern.compile("<img[^>]+src=\"(https://img\\.tnaflix[^\"]+)\"");
    private static final Pattern AUTHOR = Pattern.compile("href=\"https://www\\.tnaflix\\.com/profile/[^\"]*\"[^>]*>([^<]+)<");
    private static final Pattern VIEWS = Pattern.compile("icon-eye\"></i>\\s*([^<]+)<");
    private static final Pattern SOURCE = Pattern.compile("<source[^>]+src=\"([^\"]+)\"[^>]*size=\"(\\d+)\"");
    private static final Pattern OG_TITLE = Pattern.compile("property=\"og:title\"\\s+content=\"([^\"]*)\"");

    private Tnaflix() {
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
        String url = SITE + "/search?what=" + URLEncoder.encode(query, "UTF-8") + (page > 1 ? "&page=" + page : "");
        String html = com.orvid.adult.Web.get(circuit, url, "tnaflix search", null);
        Page p = new Page();
        java.util.Set<String> seen = new java.util.HashSet<>();
        String[] blocks = html.split("data-vid=\"");
        for (int i = 1; i < blocks.length; i++) {
            String b = blocks[i];
            Matcher href = HREF.matcher(b);
            Matcher title = TITLE.matcher(b);
            if (!href.find() || !title.find() || !seen.add(href.group(1))) continue;
            StreamInfoItem item = new StreamInfoItem(SERVICE_ID, href.group(1), unescape(title.group(1).trim()),
                    StreamType.VIDEO_STREAM);
            Matcher author = AUTHOR.matcher(b);
            item.setUploaderName(author.find() ? unescape(author.group(1).trim()) : "");
            Matcher duration = DURATION.matcher(b);
            item.setDuration(duration.find() ? seconds(duration.group(1)) : -1);
            Matcher views = VIEWS.matcher(b);
            item.setViewCount(views.find() ? count(views.group(1).trim()) : -1);
            List<Image> thumbs = new ArrayList<>();
            Matcher thumb = THUMB.matcher(b);
            if (thumb.find()) thumbs.add(new Image(thumb.group(1), 150, 300, Image.ResolutionLevel.MEDIUM));
            item.setThumbnails(thumbs);
            p.items.add(item);
        }
        if (!p.items.isEmpty() && html.contains("page=" + (page + 1))) p.next = Integer.toString(page + 1);
        return p;
    }

    public static Media resolve(String circuit, String url) throws IOException {
        if (!handles(url)) throw new IOException("not a video link");
        String html = com.orvid.adult.Web.get(circuit, url, "tnaflix video", null);
        Media m = new Media();
        Matcher t = OG_TITLE.matcher(html);
        if (t.find()) m.title = unescape(t.group(1));
        int cap = Fetch.torMode() ? 720 : 1080;
        int best = -1;
        Matcher s = SOURCE.matcher(html);
        while (s.find()) {
            int h = Integer.parseInt(s.group(2));
            if (h <= cap && h > best) {
                best = h;
                m.mp4 = unescape(s.group(1));
            }
        }
        if (m.mp4 == null) throw new IOException("no stream");
        return m;
    }

    static long seconds(String text) {
        long total = 0;
        try {
            for (String part : text.trim().split(":")) total = total * 60 + Long.parseLong(part);
            return total;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static long count(String text) {
        try {
            String t = text.toUpperCase().replace(",", "");
            double mult = t.endsWith("K") ? 1e3 : t.endsWith("M") ? 1e6 : t.endsWith("B") ? 1e9 : 1;
            if (mult > 1) t = t.substring(0, t.length() - 1);
            return (long) (Double.parseDouble(t) * mult);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String unescape(String s) {
        return s.replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">");
    }

}
