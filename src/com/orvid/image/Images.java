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
import java.util.concurrent.LinkedBlockingDeque;
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
        pool = new ThreadPoolExecutor(8, 8, 30, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>() {
            @Override
            public boolean offer(Runnable job) {
                return super.offerFirst(job);
            }
        });
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
        pool.execute(() -> {
            byte[] bytes = obtain(smaller(url, targetWidth), page);
            if (bytes == null || !key.equals(view.getTag(R.id.image_key))) return;
            Bitmap bitmap = decode(bytes, targetWidth);
            if (bitmap == null) return;
            memory.put(key, bitmap);
            main.post(() -> {
                if (key.equals(view.getTag(R.id.image_key))) view.setImageBitmap(bitmap);
            });
        });
    }

    public void prefetch(String url, int targetWidth, String page) {
        if (url == null || url.isEmpty() || memory.get(url + "@" + targetWidth) != null) return;
        String wanted = smaller(url, targetWidth);
        if (raw.get(wanted) != null) return;
        prefetchPool.execute(() -> obtain(wanted, page));
    }

    private final LruCache<String, byte[]> raw = new LruCache<String, byte[]>(48 * 1024) {
        @Override
        protected int sizeOf(String key, byte[] value) {
            return Math.max(1, value.length / 1024);
        }
    };
    private final java.util.concurrent.ConcurrentHashMap<String, Object> locks = new java.util.concurrent.ConcurrentHashMap<>();
    private final ThreadPoolExecutor prefetchPool = new ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<Runnable>());

    {
        prefetchPool.allowCoreThreadTimeOut(true);
    }

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
                        byte[] body = Fetch.on(circuit, () -> fetch(wanted, referer));
                        if (body != null && body.length > 0 && decodable(body)) {
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
        if (targetWidth > 400 || !url.contains("i.ytimg.com/")) return url;
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
