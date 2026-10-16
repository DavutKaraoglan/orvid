package com.orvid.xv;


import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Xvideos {

    public static final int SERVICE_ID = 106;
    private static final String SITE = "https://www.xvideos.com";
    private static final Pattern VIDEO = Pattern.compile("xvideos\\.com/video\\.?([a-z0-9]+)(/[^\\s\"?#]*)?");
    private static final Pattern BLOCK_HREF = Pattern.compile("href=\"(/video\\.?[a-z0-9]+/[^\"]*)\"");
    private static final Pattern BLOCK_TITLE = Pattern.compile("<p class=\"title\"><a [^>]*title=\"([^\"]*)\"");
    private static final Pattern BLOCK_THUMB = Pattern.compile("data-src=\"(https://[^\"]+)\"");
    private static final Pattern BLOCK_DURATION = Pattern.compile("<span class=\"duration\">([^<]+)</span>");
    private static final Pattern BLOCK_NAME = Pattern.compile("<span class=\"name\">([^<]+)</span>");

    private Xvideos() {
    }

    public static final class Page {
        public final List<StreamInfoItem> items = new ArrayList<>();
        public String next;
    }

    public static final class Media {
        public String title = "";
        public String author = "";
        public String hls;
        public String mp4;
    }

    public static boolean handles(String url) {
        return url != null && VIDEO.matcher(url).find();
    }

    public static Page search(String circuit, String query, String next) throws IOException {
        int page = next == null ? 0 : Integer.parseInt(next);
        String url = SITE + "/?k=" + URLEncoder.encode(query, "UTF-8") + (page > 0 ? "&p=" + page : "");
        String html = com.orvid.adult.Web.get(circuit, url, "xv search", null);
        Page p = new Page();
        String[] blocks = html.split("class=\"frame-block thumb-block");
        for (int i = 1; i < blocks.length; i++) {
            String b = blocks[i];
            Matcher href = BLOCK_HREF.matcher(b);
            Matcher title = BLOCK_TITLE.matcher(b);
            if (!href.find() || !title.find()) continue;
            StreamInfoItem item = new StreamInfoItem(SERVICE_ID, SITE + href.group(1), unescape(title.group(1)),
                    StreamType.VIDEO_STREAM);
            Matcher name = BLOCK_NAME.matcher(b);
            item.setUploaderName(name.find() ? unescape(name.group(1)).trim() : "");
            Matcher duration = BLOCK_DURATION.matcher(b);
            item.setDuration(duration.find() ? seconds(duration.group(1)) : -1);
            item.setViewCount(-1);
            List<Image> thumbs = new ArrayList<>();
            Matcher thumb = BLOCK_THUMB.matcher(b);
            if (thumb.find()) thumbs.add(new Image(thumb.group(1), 360, 640, Image.ResolutionLevel.MEDIUM));
            item.setThumbnails(thumbs);
            p.items.add(item);
        }
        if (!p.items.isEmpty() && html.contains("next-page")) p.next = Integer.toString(page + 1);
        return p;
    }

    public static Media resolve(String circuit, String url) throws IOException {
        Matcher m = VIDEO.matcher(url);
        if (!m.find()) throw new IOException("not a video link");
        String html = com.orvid.adult.Web.get(circuit, url, "xv video", null);
        Media out = new Media();
        out.title = unescape(call(html, "setVideoTitle"));
        out.author = unescape(call(html, "setUploaderName"));
        out.hls = emptyToNull(call(html, "setVideoHLS"));
        out.mp4 = emptyToNull(call(html, "setVideoUrlHigh"));
        if (out.mp4 == null) out.mp4 = emptyToNull(call(html, "setVideoUrlLow"));
        if (out.hls == null && out.mp4 == null) throw new IOException("no stream");
        return out;
    }

    private static String call(String html, String name) {
        Matcher m = Pattern.compile(name + "\\('([^']*)'\\)").matcher(html);
        return m.find() ? m.group(1) : "";
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    static long seconds(String text) {
        long total = 0;
        Matcher m = Pattern.compile("(\\d+)\\s*(h|min|sec|s)").matcher(text);
        boolean any = false;
        while (m.find()) {
            any = true;
            long n = Long.parseLong(m.group(1));
            String unit = m.group(2);
            total += unit.equals("h") ? n * 3600 : unit.equals("min") ? n * 60 : n;
        }
        return any ? total : -1;
    }

    static String unescape(String s) {
        if (s == null) return "";
        Matcher m = Pattern.compile("&#0*(\\d+);").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(Integer.parseInt(m.group(1))))));
        m.appendTail(sb);
        return sb.toString().replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&apos;", "'");
    }

}
