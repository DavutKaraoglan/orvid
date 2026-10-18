package com.orvid.play;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayDeque;

public final class Queue {

    private static final int LIMIT = 200;
    private static final ArrayDeque<StreamInfoItem> ITEMS = new ArrayDeque<>();

    private Queue() {
    }

    public static synchronized int playNext(StreamInfoItem item) {
        remove(item.getUrl());
        ITEMS.addFirst(item);
        trim();
        return ITEMS.size();
    }

    public static synchronized int add(StreamInfoItem item) {
        remove(item.getUrl());
        ITEMS.addLast(item);
        trim();
        return ITEMS.size();
    }

    public static synchronized StreamInfoItem poll() {
        return ITEMS.pollFirst();
    }

    public static synchronized StreamInfoItem peek() {
        return ITEMS.peekFirst();
    }

    public static synchronized boolean isEmpty() {
        return ITEMS.isEmpty();
    }

    public static synchronized void clear() {
        ITEMS.clear();
    }

    private static void remove(String url) {
        ITEMS.removeIf(i -> i.getUrl().equals(url));
    }

    private static void trim() {
        while (ITEMS.size() > LIMIT) ITEMS.pollLast();
    }
}
