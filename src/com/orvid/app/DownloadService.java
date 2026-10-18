package com.orvid.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;

import com.orvid.R;
import com.orvid.net.Diag;
import com.orvid.net.Fetch;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public final class DownloadService extends Service {

    public static final class Request {
        public final String title;
        public final String name;
        public final String mime;
        public final List<String> urls = new ArrayList<>();
        public final List<Long> sizes = new ArrayList<>();
        public String referer;
        public String cookie;
        public String circuit;
        public String userAgent = Fetch.USER_AGENT;
        public boolean remux;

        public Request(String title, String name, String mime) {
            this.title = title;
            this.name = name;
            this.mime = mime;
        }

        public Request add(String url, long size) {
            urls.add(url);
            sizes.add(size);
            return this;
        }
    }

    private static final String CHANNEL = "downloads";
    private static final String ACTION_CANCEL = "com.orvid.download.CANCEL";
    private static final int RUNNING_ID = 41;
    private static final long PART = 4L * 1024 * 1024;
    private static final int WORKERS = 3;
    private static final int PART_TRIES = 6;
    private static final ArrayDeque<Request> QUEUE = new ArrayDeque<>();
    private static boolean busy;
    private static volatile boolean exitWhenDone;
    private static int finishedId = 100;

    private volatile boolean cancelled;
    private volatile int lastStart;
    private Thread worker;

    public static void enqueue(Context context, Request r) {
        synchronized (QUEUE) {
            QUEUE.add(r);
        }
        Intent i = new Intent(context, DownloadService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
        else context.startService(i);
    }

    public static boolean active() {
        synchronized (QUEUE) {
            return busy || !QUEUE.isEmpty();
        }
    }

    public static void exitWhenDone() {
        exitWhenDone = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        channel();
        lastStart = startId;
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancelled = true;
            synchronized (QUEUE) {
                if (worker == null) stopSelf(startId);
            }
            return START_NOT_STICKY;
        }
        startForeground(RUNNING_ID, progress(getString(R.string.download_running), "", -1));
        synchronized (QUEUE) {
            if (worker == null) {
                worker = new Thread(this::drain, "orvid-download");
                worker.start();
            }
        }
        return START_NOT_STICKY;
    }

    private void drain() {
        while (true) {
            Request r;
            synchronized (QUEUE) {
                r = QUEUE.poll();
                busy = r != null;
                if (r == null) {
                    worker = null;
                    stopForeground(true);
                    stopSelfResult(lastStart);
                    break;
                }
            }
            cancelled = false;
            File dir = new File(getCacheDir(), "dl");
            try {
                Uri saved = run(r, dir);
                finished(r, saved);
            } catch (Exception e) {
                Diag.mark("download FAIL " + e);
                if (!cancelled) failed(r);
            } finally {
                File[] left = dir.listFiles();
                if (left != null) for (File f : left) f.delete();
            }
        }
        if (exitWhenDone && !active()) OrvidApp.get().wipeAndExit();
    }

    private Uri run(Request r, File dir) throws Exception {
        dir.mkdirs();
        long total = 0;
        for (long s : r.sizes) total += Math.max(0, s);
        AtomicLong got = new AtomicLong();
        long[] known = {total};
        List<File> parts = new ArrayList<>();
        Thread ticker = new Thread(() -> tick(r, got, known));
        ticker.setDaemon(true);
        ticker.start();
        try {
            for (int i = 0; i < r.urls.size(); i++) {
                File f = new File(dir, "part" + i);
                long size = fetch(r, r.urls.get(i), r.sizes.get(i), f, got);
                if (r.sizes.get(i) <= 0) known[0] += size;
                parts.add(f);
            }
        } finally {
            ticker.interrupt();
        }
        check();
        File out = parts.get(0);
        if (r.remux || parts.size() > 1) {
            notify(RUNNING_ID, progress(r.title, getString(R.string.download_merging), -1));
            out = new File(dir, "out");
            mux(parts, out);
        }
        check();
        return publish(out, r.name, r.mime);
    }

    private void check() throws IOException {
        if (cancelled) throw new IOException("cancelled");
    }

    private void tick(Request r, AtomicLong got, long[] known) {
        long last = 0;
        long lastAt = System.currentTimeMillis();
        try {
            while (true) {
                Thread.sleep(1000);
                long now = got.get();
                long at = System.currentTimeMillis();
                long speed = (now - last) * 1000 / Math.max(1, at - lastAt);
                last = now;
                lastAt = at;
                int percent = known[0] > 0 ? (int) Math.min(100, now * 100 / known[0]) : -1;
                String text = mb(now) + (known[0] > 0 ? " / " + mb(known[0]) : "") + ", " + mb(speed) + "/s";
                notify(RUNNING_ID, progress(r.title, text, percent));
            }
        } catch (InterruptedException ignored) {
        }
    }

    private static String mb(long bytes) {
        return String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / 1048576.0);
    }

    private long fetch(Request r, String url, long size, File out, AtomicLong got) throws Exception {
        if (size <= 0) size = length(r, url, r.circuit != null ? r.circuit : "dl0");
        if (size <= 0) return stream(r, url, out, got);
        try (RandomAccessFile raf = new RandomAccessFile(out, "rw")) {
            raf.setLength(size);
        }
        final long total = size;
        int count = (int) ((size + PART - 1) / PART);
        java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
        List<Future<Object>> jobs = new ArrayList<>();
        try {
            for (int w = 0; w < WORKERS; w++) {
                final String circuit = r.circuit != null ? r.circuit : Fetch.newCircuit("dl" + w);
                jobs.add(pool.submit(() -> {
                    try (RandomAccessFile raf = new RandomAccessFile(out, "rw")) {
                        for (int p = next.getAndIncrement(); p < count; p = next.getAndIncrement()) {
                            long from = p * PART;
                            long to = Math.min(total, from + PART) - 1;
                            part(r, url, circuit, raf, from, to, got);
                        }
                    }
                    return null;
                }));
            }
            for (Future<Object> j : jobs) j.get();
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof Exception ? (Exception) cause : new IOException(cause);
        } finally {
            pool.shutdownNow();
        }
        return size;
    }

    private void part(Request r, String url, String circuit, RandomAccessFile raf, long from, long to, AtomicLong got)
            throws Exception {
        IOException last = null;
        for (int attempt = 0; attempt < PART_TRIES; attempt++) {
            check();
            long[] n = {0};
            try {
                Fetch.on(circuit, () -> {
                    HttpURLConnection c = open(r, url);
                    c.setRequestProperty("Range", "bytes=" + from + "-" + to);
                    int code = c.getResponseCode();
                    if (code != 206) throw new IOException("HTTP " + code);
                    byte[] buf = new byte[65536];
                    try (InputStream in = c.getInputStream()) {
                        for (int k = in.read(buf); k > 0; k = in.read(buf)) {
                            if (cancelled) throw new IOException("cancelled");
                            synchronized (raf) {
                                raf.seek(from + n[0]);
                                raf.write(buf, 0, k);
                            }
                            n[0] += k;
                            got.addAndGet(k);
                        }
                    }
                    return null;
                });
                if (n[0] == to - from + 1) return;
                last = new IOException("short read " + n[0]);
            } catch (IOException e) {
                last = e;
            }
            got.addAndGet(-n[0]);
            check();
            if (r.circuit == null) Fetch.rotate(circuit);
        }
        throw last;
    }

    private long length(Request r, String url, String circuit) throws Exception {
        return Fetch.on(circuit, () -> {
            HttpURLConnection c = open(r, url);
            c.setRequestProperty("Range", "bytes=0-0");
            int code = c.getResponseCode();
            String range = c.getHeaderField("Content-Range");
            c.disconnect();
            if (code == 206 && range != null && range.contains("/") && !range.endsWith("*")) {
                return Long.parseLong(range.substring(range.lastIndexOf('/') + 1).trim());
            }
            return -1L;
        });
    }

    private long stream(Request r, String url, File out, AtomicLong got) throws Exception {
        return Fetch.on(r.circuit != null ? r.circuit : Fetch.newCircuit("dl"), () -> {
            HttpURLConnection c = open(r, url);
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            long n = 0;
            byte[] buf = new byte[65536];
            try (InputStream in = c.getInputStream(); OutputStream o = new java.io.FileOutputStream(out)) {
                for (int k = in.read(buf); k > 0; k = in.read(buf)) {
                    if (cancelled) throw new IOException("cancelled");
                    o.write(buf, 0, k);
                    n += k;
                    got.addAndGet(k);
                }
            }
            return n;
        });
    }

    private static HttpURLConnection open(Request r, String url) throws IOException {
        HttpURLConnection c = Fetch.open(url);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", r.userAgent);
        c.setRequestProperty("Accept-Encoding", "identity");
        if (r.cookie != null) c.setRequestProperty("Cookie", r.cookie);
        if (r.referer != null) {
            c.setRequestProperty("Referer", r.referer);
            c.setRequestProperty("Origin", r.referer.replaceAll("/$", ""));
        }
        return c;
    }

    static void mux(List<File> inputs, File out) throws IOException {
        MediaMuxer muxer = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        List<MediaExtractor> extractors = new ArrayList<>();
        List<Integer> tracks = new ArrayList<>();
        int buffer = 1 << 20;
        try {
            for (File f : inputs) {
                MediaExtractor ex = new MediaExtractor();
                ex.setDataSource(f.getPath());
                extractors.add(ex);
                for (int t = 0; t < ex.getTrackCount(); t++) {
                    MediaFormat format = ex.getTrackFormat(t);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime == null || !mime.startsWith("video/") && !mime.startsWith("audio/")) continue;
                    ex.selectTrack(t);
                    tracks.add(muxer.addTrack(format));
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        buffer = Math.max(buffer, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                    }
                    break;
                }
            }
            if (tracks.size() != extractors.size()) throw new IOException("no media track");
            muxer.start();
            ByteBuffer data = ByteBuffer.allocate(buffer);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean[] ended = new boolean[extractors.size()];
            while (true) {
                int pick = -1;
                long earliest = Long.MAX_VALUE;
                for (int i = 0; i < extractors.size(); i++) {
                    if (ended[i]) continue;
                    long t = extractors.get(i).getSampleTime();
                    if (t < 0) {
                        ended[i] = true;
                        continue;
                    }
                    if (t < earliest) {
                        earliest = t;
                        pick = i;
                    }
                }
                if (pick < 0) break;
                MediaExtractor ex = extractors.get(pick);
                info.offset = 0;
                info.size = ex.readSampleData(data, 0);
                if (info.size < 0) {
                    ended[pick] = true;
                    continue;
                }
                info.presentationTimeUs = ex.getSampleTime();
                info.flags = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                muxer.writeSampleData(tracks.get(pick), data, info);
                ex.advance();
            }
            muxer.stop();
        } finally {
            for (MediaExtractor ex : extractors) ex.release();
            muxer.release();
        }
    }

    private Uri publish(File file, String name, String mime) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Orvid");
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("cannot create download");
            try (InputStream in = new FileInputStream(file); OutputStream out = getContentResolver().openOutputStream(uri)) {
                copy(in, out);
            } catch (IOException e) {
                getContentResolver().delete(uri, null, null);
                throw e;
            }
            v.clear();
            v.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(uri, v, null, null);
            return uri;
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Orvid");
        dir.mkdirs();
        File target = new File(dir, name);
        try (InputStream in = new FileInputStream(file); OutputStream out = new java.io.FileOutputStream(target)) {
            copy(in, out);
        }
        Uri[] found = new Uri[1];
        java.util.concurrent.CountDownLatch scanned = new java.util.concurrent.CountDownLatch(1);
        android.media.MediaScannerConnection.scanFile(this, new String[] {target.getPath()}, new String[] {mime},
                (path, uri) -> {
                    found[0] = uri;
                    scanned.countDown();
                });
        try {
            scanned.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        return found[0];
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        if (out == null) throw new IOException("cannot write download");
        byte[] buf = new byte[1 << 16];
        for (int n = in.read(buf); n > 0; n = in.read(buf)) out.write(buf, 0, n);
    }

    private void channel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel c = new NotificationChannel(CHANNEL, getString(R.string.download_channel),
                NotificationManager.IMPORTANCE_LOW);
        c.setShowBadge(false);
        nm.createNotificationChannel(c);
    }

    private Notification.Builder builder() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_download_white).setVisibility(Notification.VISIBILITY_PRIVATE);
    }

    private Notification progress(String title, String text, int percent) {
        PendingIntent cancel = PendingIntent.getService(this, 0,
                new Intent(this, DownloadService.class).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return builder().setContentTitle(title).setContentText(text).setOngoing(true).setOnlyAlertOnce(true)
                .setProgress(100, Math.max(0, percent), percent < 0)
                .addAction(new Notification.Action.Builder(null, getString(R.string.download_cancel), cancel).build())
                .build();
    }

    private void finished(Request r, Uri saved) {
        Notification.Builder b = builder().setContentTitle(getString(R.string.download_done)).setContentText(r.title)
                .setAutoCancel(true);
        if (saved != null) {
            Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(saved, r.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            b.setContentIntent(PendingIntent.getActivity(this, finishedId, view, PendingIntent.FLAG_IMMUTABLE));
        }
        notify(finishedId++, b.build());
    }

    private void failed(Request r) {
        notify(finishedId++, builder().setContentTitle(getString(R.string.download_failed)).setContentText(r.title)
                .setAutoCancel(true).build());
    }

    private void notify(int id, Notification n) {
        getSystemService(NotificationManager.class).notify(id, n);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
