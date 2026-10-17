package com.orvid.app;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class DevInfo {

    public static volatile boolean on;
    private static final Map<Object, String> RANK = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, String> THUMB = new ConcurrentHashMap<>();
    private static final Map<String, String> WARM = new ConcurrentHashMap<>();

    private DevInfo() {
    }

    public static void rank(Object item, int source, int position, double score) {
        if (on) RANK.put(item, String.format(java.util.Locale.ROOT, "src%d #%d s=%.1f", source, position + 1, score));
    }

    public static String rank(Object item) {
        return RANK.get(item);
    }

    public static void thumb(String page, int bytes, long ms) {
        if (on && page != null) THUMB.put(page, bytes / 1024 + "KB " + ms + "ms");
    }

    public static String thumb(String page) {
        return page == null ? null : THUMB.get(page);
    }

    public static void warm(String page, String state) {
        if (page != null) WARM.put(page, state);
    }

    public static String warm(String page) {
        return page == null ? null : WARM.get(page);
    }
}
