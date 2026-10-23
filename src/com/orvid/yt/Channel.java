package com.orvid.yt;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.orvid.net.Diag;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Channel {

    private static final String BROWSE_URL = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false";
    private static final String VIDEOS_TAB = "EgZ2aWRlb3PyBgQKAjoA";
    private static final Pattern ID = Pattern.compile("youtube\\.com/channel/(UC[A-Za-z0-9_-]{22})");
    private static final String ITEMS = "(continuationItemRenderer,richItemRenderer.content." + Related.VIDEO_FIELDS + ")";
    private static final String FIRST_MASK = "metadata.channelMetadataRenderer(title,avatar),"
            + "header.pageHeaderRenderer.content.pageHeaderViewModel.metadata,"
            + "contents.singleColumnBrowseResultsRenderer.tabs.tabRenderer.content.richGridRenderer.contents" + ITEMS;
    private static final String MORE_MASK = "onResponseReceivedActions.appendContinuationItemsAction.continuationItems" + ITEMS;

    public static final class Page {
        public String name;
        public String avatar;
        public long subscribers = -1;
        public final List<StreamInfoItem> items = new ArrayList<>();
        public String continuation;
    }

    private Channel() {
    }

    public static String id(String url) {
        if (url == null) return null;
        Matcher m = ID.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public static Page first(String circuit, String channelId) throws IOException {
        JsonObject body = new JsonObject();
        body.put("browseId", channelId);
        body.put("params", VIDEOS_TAB);
        Page p = fetch(circuit, body, "channel", FIRST_MASK);
        String url = "https://www.youtube.com/channel/" + channelId;
        for (StreamInfoItem i : p.items) own(i, p.name, url);
        return p;
    }

    public static Page more(String circuit, String token, String name, String channelId) throws IOException {
        JsonObject body = new JsonObject();
        body.put("continuation", token);
        Page p = fetch(circuit, body, "channel more", MORE_MASK);
        String url = "https://www.youtube.com/channel/" + channelId;
        for (StreamInfoItem i : p.items) own(i, name, url);
        return p;
    }

    private static void own(StreamInfoItem i, String name, String url) {
        if (name != null && (i.getUploaderName() == null || i.getUploaderName().isEmpty())) i.setUploaderName(name);
        if (i.getUploaderUrl() == null) i.setUploaderUrl(url);
    }

    private static Page fetch(String circuit, JsonObject body, String label, String mask) throws IOException {
        JsonObject copy = new JsonObject();
        copy.putAll(body);
        Page p = parse(Related.post(circuit, BROWSE_URL, body, label, mask));
        if (!p.items.isEmpty() || p.continuation != null) return p;
        Diag.mark(label + ": nothing under the field mask, asking for the full answer");
        return parse(Related.post(circuit, BROWSE_URL, copy, label, null));
    }

    private static Page parse(String text) throws IOException {
        Object root = Related.parse(text);
        Page p = new Page();
        Related.collect(root, p.items);
        p.continuation = Search.token(root);
        if (root instanceof JsonObject) header((JsonObject) root, p);
        return p;
    }

    private static void header(JsonObject root, Page p) {
        JsonObject meta = root.getObject("metadata").getObject("channelMetadataRenderer");
        String title = meta.getString("title", null);
        if (title != null && !title.isEmpty()) p.name = title;
        JsonArray thumbs = meta.getObject("avatar").getArray("thumbnails");
        int best = -1;
        for (int i = 0; i < thumbs.size(); i++) {
            JsonObject t = thumbs.getObject(i);
            int w = t.getInt("width", 0);
            if (t.getString("url", null) != null && w > best) {
                best = w;
                p.avatar = sized(t.getString("url"));
            }
        }
        JsonArray rows = root.getObject("header").getObject("pageHeaderRenderer").getObject("content")
                .getObject("pageHeaderViewModel").getObject("metadata").getObject("contentMetadataViewModel")
                .getArray("metadataRows");
        for (int r = 0; r < rows.size(); r++) {
            JsonArray parts = rows.getObject(r).getArray("metadataParts");
            for (int k = 0; k < parts.size(); k++) {
                String text = parts.getObject(k).getObject("text").getString("content", "");
                if (text.toLowerCase(java.util.Locale.ROOT).contains("subscriber")) {
                    p.subscribers = com.orvid.search.Counts.views(text, "en");
                }
            }
        }
    }

    private static String sized(String url) {
        if (url.startsWith("//")) url = "https:" + url;
        return url.replaceFirst("=s\\d+-", "=s176-");
    }
}
