package com.orvid.net;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Prewarm {

    private static final long FRESH_MS = 4 * 60 * 1000L;
    private static final int TIMEOUT_MS = 20000;

    private static final class Warm {
        final String circuit;
        volatile long at;
        volatile boolean ready;

        Warm(String circuit) {
            this.circuit = circuit;
            this.at = System.currentTimeMillis();
        }
    }

    private static final Map<String, Warm> WARM = new HashMap<>();
    private static final Map<String, String> HOSTS = new HashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(6, Focus.background("orvid-prewarm"));

    private Prewarm() {
    }

    public static void learn(String key, String host) {
        if (host == null) return;
        synchronized (WARM) {
            HOSTS.put(key, host);
        }
    }

    public static String host(String key) {
        synchronized (WARM) {
            return HOSTS.get(key);
        }
    }

    public static void warm(String key, String host) {
        if (host == null || !Fetch.torMode()) return;
        long now = System.currentTimeMillis();
        Warm w;
        synchronized (WARM) {
            Warm old = WARM.get(key);
            if (old != null && now - old.at < FRESH_MS) return;
            w = new Warm(Fetch.newCircuit(key));
            WARM.put(key, w);
        }
        POOL.execute(() -> {
            long began = System.currentTimeMillis();
            try {
                int code = Fetch.on(w.circuit, () -> {
                    HttpURLConnection c = Fetch.open("https://" + host + "/");
                    c.setRequestMethod("HEAD");
                    c.setInstanceFollowRedirects(false);
                    c.setConnectTimeout(TIMEOUT_MS);
                    c.setReadTimeout(TIMEOUT_MS);
                    c.setRequestProperty("User-Agent", Fetch.USER_AGENT);
                    int r = c.getResponseCode();
                    java.io.InputStream in = r < 400 ? c.getInputStream() : c.getErrorStream();
                    if (in != null) in.close();
                    return r;
                });
                w.ready = true;
                w.at = System.currentTimeMillis();
                Diag.mark("prewarm " + key + " " + code + " " + (w.at - began) + "ms");
            } catch (Exception e) {
                synchronized (WARM) {
                    if (WARM.get(key) == w) WARM.remove(key);
                }
                Diag.mark("prewarm " + key + " FAIL " + e.getMessage());
            }
        });
    }

    public static String take(String key) {
        long now = System.currentTimeMillis();
        synchronized (WARM) {
            Warm w = WARM.get(key);
            if (w != null && now - w.at < FRESH_MS) {
                w.at = now;
                return w.circuit;
            }
        }
        return Fetch.newCircuit(key);
    }
}
