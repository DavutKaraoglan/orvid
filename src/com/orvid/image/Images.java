package com.orvid.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.orvid.R;
import com.orvid.net.Fetch;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class Images {

    private final File dir;
    private final LruCache<String, Bitmap> memory;
    private final ThreadPoolExecutor pool;
    private final Handler main = new Handler(Looper.getMainLooper());
    public void clearDisk() {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    public Images(File dir) {
        this.dir = dir;
        int kilobytes = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        memory = new LruCache<String, Bitmap>(kilobytes) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
        pool = new ThreadPoolExecutor(12, 12, 30, TimeUnit.SECONDS, new java.util.concurrent.PriorityBlockingQueue<Runnable>());
    }

    private static final String WARM_URL = "https://i.ytimg.com/vi/jNQXAC9IVRw/default.jpg";
    private static final int WARM_CONNECTIONS = 12;
    private static final long WARM_EVERY_MS = 60000;
    private volatile long warmedAt;
    private final java.util.concurrent.ExecutorService warmer = java.util.concurrent.Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "orvid-warm");
        t.setDaemon(true);
        return t;
    });

    public void warm() {
        long now = System.currentTimeMillis();
        if (now - warmedAt < WARM_EVERY_MS) return;
        warmedAt = now;
        String circuit = circuitFor(WARM_URL);
        for (int i = 0; i < WARM_CONNECTIONS; i++) {
            warmer.execute(() -> {
                try {
                    Fetch.on(circuit, () -> download(WARM_URL, null));
                } catch (Exception ignored) {
                }
            });
        }
    }

    private final java.util.concurrent.atomic.AtomicLong order = new java.util.concurrent.atomic.AtomicLong();

    private final java.util.Set<String> ahead = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final long BATCH_MS = 120;
    private static final int VISIBLE = 0;
    private static final int AHEAD = 1;

    private final class Job implements Runnable, Comparable<Job> {
        final int priority;
        final long seq = order.incrementAndGet();
        final long batch = android.os.SystemClock.uptimeMillis() / BATCH_MS;
        final Runnable work;

        Job(int priority, Runnable work) {
            this.priority = priority;
            this.work = work;
        }

        @Override
        public void run() {
            work.run();
        }

        @Override
        public int compareTo(Job other) {
            if (priority != other.priority) return Integer.compare(priority, other.priority);
            if (priority == VISIBLE && batch != other.batch) return Long.compare(other.batch, batch);
            return Long.compare(seq, other.seq);
        }
    }

    public void load(final ImageView view, final String url, final int targetWidth, final String page) {
        if (url == null || url.isEmpty()) {
            view.setTag(R.id.image_key, null);
            view.setImageDrawable(null);
            return;
        }
        final String key = url + "@" + targetWidth;
        if (key.equals(view.getTag(R.id.image_key)) && view.getDrawable() != null) return;
        view.setTag(R.id.image_key, key);
        Bitmap cached = memory.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        java.util.Set<ImageView> fresh = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.Set<ImageView> views = waiting.putIfAbsent(key, fresh);
        if (views != null) {
            views.add(view);
            Bitmap done = memory.get(key);
            if (done != null) view.setImageBitmap(done);
            return;
        }
        fresh.add(view);
        String wanted = smaller(url, targetWidth);
        pool.execute(new Job(VISIBLE, () -> {
            try {
                if (!wanted(key)) return;
                byte[] bytes = obtain(wanted, page);
                Bitmap bitmap = bytes == null ? null : decode(bytes, targetWidth);
                if (bitmap == null) return;
                memory.put(key, bitmap);
                deliver(key, bitmap);
            } finally {
                waiting.remove(key);
            }
        }));
    }

    private final java.util.concurrent.ConcurrentHashMap<String, java.util.Set<ImageView>> waiting = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean wanted(String key) {
        java.util.Set<ImageView> views = waiting.get(key);
        if (views == null) return false;
        views.removeIf(v -> !key.equals(v.getTag(R.id.image_key)));
        return !views.isEmpty();
    }

    private void deliver(String key, Bitmap bitmap) {
        java.util.Set<ImageView> views = waiting.get(key);
        if (views == null) return;
        java.util.List<ImageView> targets = new java.util.ArrayList<>(views);
        main.post(() -> {
            for (ImageView v : targets) {
                if (key.equals(v.getTag(R.id.image_key))) v.setImageBitmap(bitmap);
            }
        });
    }

    public void prefetch(String url, int targetWidth, String page) {
        if (url == null || url.isEmpty() || memory.get(url + "@" + targetWidth) != null) return;
        String wanted = smaller(url, targetWidth);
        if (raw.get(wanted) != null) return;
        if (!ahead.add(wanted)) return;
        pool.execute(new Job(AHEAD, () -> {
            try {
                obtain(wanted, page);
            } finally {
                ahead.remove(wanted);
            }
        }));
    }

    private final LruCache<String, byte[]> raw = new LruCache<String, byte[]>(48 * 1024) {
        @Override
        protected int sizeOf(String key, byte[] value) {
            return Math.max(1, value.length / 1024);
        }
    };
    private final java.util.concurrent.ConcurrentHashMap<String, Object> locks = new java.util.concurrent.ConcurrentHashMap<>();

    private final CircuitHealth health = new CircuitHealth();

    private byte[] obtain(String wanted, String page) {
        byte[] known = raw.get(wanted);
        if (known != null) return known;
        Object lock = locks.computeIfAbsent(wanted, k -> new Object());
        try {
            synchronized (lock) {
                known = raw.get(wanted);
                if (known != null) return known;
                String circuit = circuitFor(wanted);
                String referer = referer(page);
                for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                    try {
                        long began = android.os.SystemClock.uptimeMillis();
                        byte[] body = Fetch.on(circuit, () -> fetch(wanted, referer));
                        if (body != null && body.length > 0 && decodable(body)) {
                            long now = android.os.SystemClock.uptimeMillis();
                            if (health.record(circuit, now - began, true, now)) Fetch.rotate(circuit);
                            raw.put(wanted, body);
                            return body;
                        }
                    } catch (Throwable ignored) {
                    }
                    if (attempt + 1 < ATTEMPTS) Fetch.rotate(circuit);
                }
                return null;
            }
        } finally {
            locks.remove(wanted, lock);
        }
    }

    private static boolean decodable(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        return bounds.outWidth > 0;
    }

    private static final int IMAGE_CIRCUITS = 3;
    private static final int ATTEMPTS = 3;

    private static String referer(String page) {
        if (page == null) return null;
        android.net.Uri u = android.net.Uri.parse(page);
        if (u.getScheme() == null || u.getHost() == null || u.getHost().endsWith("youtube.com")) return null;
        return u.getScheme() + "://" + u.getHost() + "/";
    }

    private static byte[] download(String url, String referer) throws IOException {
        java.net.HttpURLConnection c = Fetch.open(url);
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", Fetch.USER_AGENT);
        c.setRequestProperty("Accept", "image/webp,image/jpeg,image/png,image/*;q=0.8");
        if (referer != null) c.setRequestProperty("Referer", referer);
        int code = c.getResponseCode();
        if (code >= 400) throw new IOException("HTTP " + code);
        try (java.io.InputStream in = c.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static String circuitFor(String url) {
        String host = android.net.Uri.parse(url).getHost();
        int slot = Math.floorMod(host == null ? 0 : host.hashCode(), IMAGE_CIRCUITS);
        return "images-" + slot;
    }

    private static String smaller(String url, int targetWidth) {
        if (targetWidth > 400) return url;
        if (url.contains(".coub.com/") && url.contains("/big_")) return url.replace("/big_", "/small_");
        if (url.contains(".pstatic.net/") && url.contains("type=o500x280")) return url.replaceFirst("type=o500x280[^&]*", "type=w320");
        if (url.contains(".porngo.com/") && url.contains("/medium@2x/")) return url.replace("/medium@2x/", "/medium/");
        if (!url.contains("i.ytimg.com/")) return url;
        String out = url.replace("/hq720.webp", "/mqdefault.webp").replace("/hq720.jpg", "/mqdefault.jpg")
                .replace("/hqdefault.webp", "/mqdefault.webp").replace("/hqdefault.jpg", "/mqdefault.jpg");
        if (out.equals(url)) return url;
        int q = out.indexOf('?');
        return q > 0 ? out.substring(0, q) : out;
    }

    private byte[] fetch(String url, String referer) throws IOException {
        try {
            return download(url, referer);
        } catch (IOException e) {
            if (!url.endsWith("/hq720.webp")) throw e;
            return download(url.replace("/hq720.webp", "/hqdefault.webp"), referer);
        }
    }

    private static Bitmap decode(byte[] bytes, int targetWidth) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        int sample = 1;
        while (targetWidth > 0 && bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2;
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
}
