package com.orvid.yt;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Search {

    private static final String SEARCH_URL = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false";

    private Search() {
    }

    public static final class Page {
        public final List<StreamInfoItem> items = new ArrayList<>();
        public String continuation;
        public String corrected;
    }

    private static final String FIRST_MASK = "contents.sectionListRenderer.contents(continuationItemRenderer,"
            + "itemSectionRenderer.contents(showingResultsForRenderer," + Related.VIDEO_FIELDS + "))";
    private static final String MORE_MASK = "onResponseReceivedCommands.appendContinuationItemsAction.continuationItems("
            + "continuationItemRenderer,itemSectionRenderer.contents(" + Related.VIDEO_FIELDS + "))";

    public static Page first(String circuit, String query, String params) throws IOException {
        JsonObject body = new JsonObject();
        body.put("query", query);
        if (params != null) body.put("params", params);
        return Related.masked(circuit, SEARCH_URL, body, "search", FIRST_MASK, Search::page);
    }

    public static Page more(String circuit, String continuation) throws IOException {
        JsonObject body = new JsonObject();
        body.put("continuation", continuation);
        return Related.masked(circuit, SEARCH_URL, body, "search more", MORE_MASK, Search::page);
    }

    private static Page page(String text) throws IOException {
        Object root = Related.parse(text);
        Page p = new Page();
        Related.collect(root, p.items);
        p.continuation = token(root);
        p.corrected = corrected(root);
        return p;
    }

    private static String corrected(Object node) {
        if (node instanceof JsonObject) {
            JsonObject o = (JsonObject) node;
            JsonObject r = o.getObject("showingResultsForRenderer", null);
            if (r != null) {
                StringBuilder sb = new StringBuilder();
                JsonArray runs = r.getObject("correctedQuery").getArray("runs");
                for (int i = 0; i < runs.size(); i++) sb.append(runs.getObject(i).getString("text", ""));
                String q = sb.toString().trim();
                return q.isEmpty() ? null : q;
            }
            for (Object child : o.values()) {
                String t = corrected(child);
                if (t != null) return t;
            }
        } else if (node instanceof JsonArray) {
            for (Object child : (JsonArray) node) {
                String t = corrected(child);
                if (t != null) return t;
            }
        }
        return null;
    }

    static String token(Object node) {
        if (node instanceof JsonObject) {
            JsonObject o = (JsonObject) node;
            JsonObject item = o.getObject("continuationItemRenderer", null);
            if (item != null) {
                String t = item.getObject("continuationEndpoint").getObject("continuationCommand")
                        .getString("token", null);
                if (t != null) return t;
            }
            for (Object child : o.values()) {
                String t = token(child);
                if (t != null) return t;
            }
        } else if (node instanceof JsonArray) {
            for (Object child : (JsonArray) node) {
                String t = token(child);
                if (t != null) return t;
            }
        }
        return null;
    }
}
