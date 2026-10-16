package com.orvid.search;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

public abstract class Source {

    public static final class Page {
        public final List<StreamInfoItem> items = new ArrayList<>();
        public String next;

        public Page() {
        }

        public Page(List<StreamInfoItem> items, String next) {
            this.items.addAll(items);
            this.next = next;
        }
    }

    public final String key;
    public final String name;
    public final int icon;
    public final int serviceId;
    public final double weight;
    public final boolean defaultOn;
    public final boolean adult;

    public static volatile boolean adultUnlocked;
    public static final int MIXED = 0;
    public static final int GAY = 2;
    public static final int TRANS = 3;
    static final java.util.Map<Integer, Source> BY_SERVICE = new java.util.concurrent.ConcurrentHashMap<>();
    public int orientation = MIXED;
    public String script;

    public Source script(String value) {
        script = value;
        return this;
    }

    public int priority(Query q) {
        if (script != null) return 2;
        return adult ? 5 : 4;
    }

    public Source orientation(int value) {
        orientation = value;
        return this;
    }
    static final java.util.Set<String> ADULT_KEYS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    protected Source(String key, String name, int icon, int serviceId, double weight, boolean defaultOn, boolean adult) {
        this.key = key;
        this.name = name;
        this.icon = icon;
        this.serviceId = serviceId;
        this.weight = weight;
        this.defaultOn = defaultOn;
        this.adult = adult;
        if (adult) ADULT_KEYS.add(key);
        BY_SERVICE.put(serviceId, this);
    }

    public abstract Page search(String circuit, String query, Filters filters, String next) throws Exception;

    public boolean wanted(Filters f, Query q) {
        if (!q.site.isEmpty() || f.type == Filters.TYPE_SHORTS) return false;
        if (adult && (f.hideAdult || !adultUnlocked)) return false;
        if (script != null && !script.equals(q.script())) return false;
        if (adult && f.orientation == Filters.ORIENT_STRAIGHT && orientation != MIXED) return false;
        if (adult && f.orientation == Filters.ORIENT_TRANS && orientation == GAY) return false;
        if (adult && f.orientation == Filters.ORIENT_GAY && orientation == TRANS) return false;
        return f.on(this);
    }
}
