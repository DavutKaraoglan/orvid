package com.orvid.play;

import com.orvid.net.Diag;
import com.orvid.net.Fetch;
import com.orvid.yt.Visionos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class Warm {

    static final class Entry {
        final byte[] head;
        final long total;

        Entry(byte[] head, long total) {
            this.head = head;
            this.total = total;
        }
    }

    private static final long BUDGET = 16L * 1024 * 1024;
    private static final int YT_VIDEO = 512 * 1024;
    private static final int YT_AUDIO = 192 * 1024;
    private static final int FILE_HEAD = 768 * 1024;
    private static final int PLAYLIST_MAX = 256 * 1024;
    private static final int SEGMENT_MAX = 1536 * 1024;
    private static final double BANDWIDTH_FRACTION = 0.7;

    public static long startBitrate() {
        return Fetch.torMode() ? 1_200_000 : 3_000_000;
    }
    private static final int YT_VIDEO_FIRST = 200 * 1024;
    private static final int YT_AUDIO_FIRST = 64 * 1024;
    private static final int FILE_FIRST = 256 * 1024;
    private static final int SEGMENT_FIRST = 512 * 1024;
    private static final int CAPTION_MAX = 512 * 1024;
    private static final long PREPARE_WAIT_S = 30;

    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(32, 0.75f, true);
    private static long size;
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2,
            com.orvid.net.Focus.background("orvid-warm-media", Thread.MIN_PRIORITY));
    private static final java.util.Set<String> STARTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private Warm() {
    }

    static Entry get(String url) {
        synchronized (CACHE) {
            return CACHE.get(url);
        }
    }

    private static void put(String url, byte[] head, long total) {
        synchronized (CACHE) {
            Entry old = CACHE.put(url, new Entry(head, total));
            if (old != null) size -= old.head.length;
            size += head.length;
            Iterator<Map.Entry<String, Entry>> it = CACHE.entrySet().iterator();
            while (size > BUDGET && it.hasNext()) {
                size -= it.next().getValue().head.length;
                it.remove();
            }
        }
    }

    static void keep(String url, byte[] head, long total) {
        Entry have = get(url);
        if (have != null && have.head.length >= head.length) return;
        put(url, head, total);
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
            size = 0;
        }
        STARTED.clear();
    }

    private static final ExecutorService WATCH = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "orvid-warm-watch");
        t.setDaemon(true);
        return t;
    });

    public static void caption(String circuit, String url) {
        if (circuit == null || url == null || get(url) != null) return;
        EXEC.execute(() -> {
            String via = CircuitDataSource.captionCircuit(circuit);
            for (int i = 0; i <= CircuitDataSource.CAPTION_RETRIES; i++) {
                try {
                    fetch(via, url, null, CAPTION_MAX, true);
                    return;
                } catch (Exception e) {
                    Diag.mark("caption warm FAIL " + e.getMessage());
                    if (!String.valueOf(e.getMessage()).contains("429")) return;
                    Fetch.rotate(via);
                }
            }
        });
    }

    public static void link(String pageUrl) {
        if (pageUrl == null || STARTED.contains(pageUrl)) return;
        String id = Visionos.videoId(pageUrl);
        Future<?> f;
        if (id != null) f = Visionos.prepare(id);
        else if (Resolve.preparable(pageUrl)) f = Resolve.prepare(pageUrl);
        else return;
        if (!com.orvid.app.DevInfo.on || f == null || com.orvid.app.DevInfo.warm(pageUrl) != null) return;
        com.orvid.app.DevInfo.warm(pageUrl, "link…");
        WATCH.execute(() -> {
            try {
                f.get(PREPARE_WAIT_S, TimeUnit.SECONDS);
                if (!STARTED.contains(pageUrl)) com.orvid.app.DevInfo.warm(pageUrl, "link ok");
            } catch (Exception e) {
                if (!STARTED.contains(pageUrl)) com.orvid.app.DevInfo.warm(pageUrl, "link fail");
            }
        });
    }

    public static void prepare(String pageUrl) {
        prepare(pageUrl, false);
    }

    public static void prepare(String pageUrl, boolean firstFrameOnly) {
        if (pageUrl == null || !STARTED.add(pageUrl)) return;
        final boolean first = firstFrameOnly;
        com.orvid.app.DevInfo.warm(pageUrl, "link…");
        String id = Visionos.videoId(pageUrl);
        if (id != null) {
            Future<Visionos.Prepared> f = Visionos.prepare(id);
            EXEC.execute(() -> {
                try {
                    Visionos.Prepared p = f.get(PREPARE_WAIT_S, TimeUnit.SECONDS);
                    com.orvid.app.DevInfo.warm(pageUrl, "link ok, media…");
                    com.orvid.app.DevInfo.warm(pageUrl, "warm " + youtube(p, first) / 1024 + "KB");
                } catch (Exception e) {
                    STARTED.remove(pageUrl);
                    com.orvid.app.DevInfo.warm(pageUrl, "fail " + e.getClass().getSimpleName());
                }
            });
            return;
        }
        Future<Resolve.Stream> f = Resolve.prepare(pageUrl);
        if (f == null) {
            com.orvid.app.DevInfo.warm(pageUrl, "not preparable");
            return;
        }
        EXEC.execute(() -> {
            try {
                Resolve.Stream s = f.get(PREPARE_WAIT_S, TimeUnit.SECONDS);
                if (s != null && s.direct != null && s.direct.url != null) {
                    com.orvid.app.DevInfo.warm(pageUrl, "link ok, media…");
                    com.orvid.app.DevInfo.warm(pageUrl, "warm " + direct(s.circuit, s.direct, first) / 1024 + "KB");
                } else {
                    com.orvid.app.DevInfo.warm(pageUrl, "no stream");
                }
            } catch (Exception e) {
                STARTED.remove(pageUrl);
                com.orvid.app.DevInfo.warm(pageUrl, "fail " + e.getClass().getSimpleName());
            }
        });
    }

    private static int youtube(Visionos.Prepared p, boolean firstFrame) throws Exception {
        Visionos.Video v = p.video;
        if (v.live) return 0;
        List<Visionos.Format> videos = Dash.videoTracks(v.formats, 2160, Codecs.vp9MaxHeight());
        Visionos.Format audio = Dash.audioTrack(v.formats);
        if (videos.isEmpty() || audio == null) return 0;
        long allowed = (long) (startBitrate() * BANDWIDTH_FRACTION);
        Visionos.Format first = null;
        Visionos.Format lowest = null;
        for (Visionos.Format f : videos) {
            if (lowest == null || f.bitrate < lowest.bitrate) lowest = f;
            if (f.bitrate <= allowed && (first == null || f.bitrate > first.bitrate)) first = f;
        }
        if (first == null) first = lowest;
        int got = fetch(p.circuit, first.url, null, firstFrame ? YT_VIDEO_FIRST : YT_VIDEO, false).length;
        got += fetch(p.circuit, audio.url, null, firstFrame ? YT_AUDIO_FIRST : YT_AUDIO, false).length;
        Diag.mark("warm yt " + first.height + "p");
        return got;
    }

    private static int direct(String circuit, Direct d, boolean firstFrame) throws Exception {
        if (d.cookie != null) return 0;
        if (!d.hls) return fetch(circuit, d.url, d.referer, firstFrame ? FILE_FIRST : FILE_HEAD, false).length;
        String playlist = text(fetch(circuit, d.url, d.referer, PLAYLIST_MAX, true));
        String media = d.url;
        if (playlist.contains("#EXT-X-STREAM-INF")) {
            media = variant(d.url, playlist);
            if (media == null) return 0;
            playlist = text(fetch(circuit, media, d.referer, PLAYLIST_MAX, true));
        }
        String init = null;
        String segment = null;
        for (String line : playlist.split("\n")) {
            line = line.trim();
            if (line.startsWith("#EXT-X-MAP:")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("URI=\"([^\"]+)\"").matcher(line);
                if (m.find()) init = java.net.URI.create(media).resolve(m.group(1)).toString();
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                segment = java.net.URI.create(media).resolve(line).toString();
                break;
            }
        }
        int got = 0;
        if (init != null) got += fetch(circuit, init, d.referer, SEGMENT_MAX, true).length;
        if (segment != null) got += fetch(circuit, segment, d.referer, firstFrame ? SEGMENT_FIRST : SEGMENT_MAX, false).length;
        return got;
    }

    private static String variant(String base, String master) {
        long allowed = (long) (startBitrate() * BANDWIDTH_FRACTION);
        String best = null;
        long bestRate = -1;
        String lowest = null;
        long lowestRate = Long.MAX_VALUE;
        long rate = -1;
        for (String line : master.split("\n")) {
            line = line.trim();
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("BANDWIDTH=(\\d+)").matcher(line);
                rate = m.find() ? Long.parseLong(m.group(1)) : -1;
            } else if (!line.isEmpty() && !line.startsWith("#") && rate >= 0) {
                String url = java.net.URI.create(base).resolve(line).toString();
                if (rate < lowestRate) {
                    lowestRate = rate;
                    lowest = url;
                }
                if (rate <= allowed && rate > bestRate) {
                    bestRate = rate;
                    best = url;
                }
                rate = -1;
            }
        }
        return best != null ? best : lowest;
    }

    private static String text(byte[] body) {
        return body == null ? "" : new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] fetch(String circuit, String url, String referer, int limit, boolean whole) throws Exception {
        Entry known = get(url);
        if (known != null) return known.head;
        return Fetch.on(circuit, () -> {
            HttpURLConnection c = Fetch.open(url);
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", url.contains("googlevideo.com") ? Visionos.USER_AGENT : Fetch.USER_AGENT);
            c.setRequestProperty("Accept-Encoding", "identity");
            if (!whole) c.setRequestProperty("Range", "bytes=0-" + (limit - 1));
            if (referer != null) {
                c.setRequestProperty("Referer", referer);
                c.setRequestProperty("Origin", referer.replaceAll("/$", ""));
            }
            int code = c.getResponseCode();
            if (code != 200 && code != 206) throw new IOException("warm HTTP " + code);
            long total = -1;
            String range = c.getHeaderField("Content-Range");
            if (range != null && range.contains("/") && !range.endsWith("*")) {
                total = Long.parseLong(range.substring(range.lastIndexOf('/') + 1).trim());
            } else if (code == 200) {
                total = c.getContentLengthLong();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[65536];
                for (int n = in.read(buf); n > 0 && out.size() < limit; n = in.read(buf)) out.write(buf, 0, n);
            }
            byte[] head = out.toByteArray();
            if (head.length > limit) head = java.util.Arrays.copyOf(head, limit);
            if (whole && total < 0 && head.length < limit) total = head.length;
            if (whole && total > head.length) return head;
            put(url, head, total);
            return head;
        });
    }
}
