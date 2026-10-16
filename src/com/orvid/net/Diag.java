package com.orvid.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Diag {
    private static final List<String> lines = new ArrayList<>();
    private static long start = System.currentTimeMillis();
    private static Runnable listener;

    private Diag() {
    }

    public static synchronized void reset() {
        lines.clear();
        start = System.currentTimeMillis();
    }

    public static void mark(String event) {
        Runnable l;
        synchronized (Diag.class) {
            lines.add(String.format(Locale.ROOT, "%6d %s", System.currentTimeMillis() - start, event));
            l = listener;
        }
        if (l != null) l.run();
    }

    public static synchronized void listen(Runnable r) {
        listener = r;
    }

    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append('\n');
        return sb.toString();
    }

    public static synchronized String tail(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, lines.size() - n); i < lines.size(); i++) sb.append(lines.get(i)).append('\n');
        return sb.toString();
    }
}
