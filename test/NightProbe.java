import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.play.*;
import com.orvid.search.Source;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

public class NightProbe {
    static final int TRIES = 3;
    static final long START_BYTES = 512 * 1024;

    static String query(Source s) {
        switch (s.key) {
            case "rutube": return "кошки";
            case "aparat": return "گربه";
            case "chzzk": case "soop": return "고양이";
            case "mediathek": return "katzen";
            case "nasa": return "mars";
            case "ccc": return "tor";
            case "steam": return "portal";
            case "ted": return "climate";
            case "bellesa": return "massage";
            case "twitch": return "minecraft";
            case "archive": return "apollo";
            case "iptv": return "news";
            case "radio": case "podcasts": case "audius": case "fma": case "openverse": case "ccmixter": case "ardsounds": return "jazz";
            case "thegay": return "muscle";
            case "shemalez": return "blonde";
            case "librivox": return "alice";
            case "hentaihaven": case "watchhentai": return "elf";
            case "camstreams": case "archivebate": return "blonde";
        }
        String q = System.getenv("Q_" + s.key);
        if (q != null) return q;
        if (s.adult) return "milf";
        return "cat";
    }

    static HttpURLConnection follow(String url, String ref, String cookie, String range) throws IOException {
        String at = url;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection h = Fetch.open(at);
            h.setInstanceFollowRedirects(false);
            h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
            h.setRequestProperty("Accept-Encoding", "identity");
            if (range != null) h.setRequestProperty("Range", range);
            if (ref != null) h.setRequestProperty("Referer", ref);
            if (cookie != null) h.setRequestProperty("Cookie", cookie);
            int code = h.getResponseCode();
            String loc = h.getHeaderField("Location");
            if (code >= 300 && code < 400 && loc != null) {
                h.disconnect();
                at = java.net.URI.create(at).resolve(loc.replace(" ", "%20")).toString().replaceFirst("^http://", "https://");
                continue;
            }
            return h;
        }
        throw new IOException("too many redirects");
    }

    static String mediaUrl(String c, String url, boolean hls, String ref, String cookie) throws Exception {
        if (!hls) return url;
        for (int depth = 0; depth < 3; depth++) {
            final String u = url;
            String[] got = Fetch.on(c, () -> {
                HttpURLConnection h = follow(u, ref, cookie, null);
                int code = h.getResponseCode();
                if (code >= 400) throw new IOException("playlist HTTP " + code);
                return new String[]{new String(h.getInputStream().readAllBytes()), h.getURL().toString()};
            });
            String pl = got[0];
            url = got[1];
            String pick = null;
            boolean master = pl.contains("#EXT-X-STREAM-INF");
            String[] lines = pl.split("\n");
            if (master) {
                long best = Long.MAX_VALUE;
                for (int i = 0; i < lines.length - 1; i++) {
                    if (!lines[i].startsWith("#EXT-X-STREAM-INF")) continue;
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("BANDWIDTH=(\\d+)").matcher(lines[i]);
                    long bw = m.find() ? Long.parseLong(m.group(1)) : 0;
                    String next = lines[i + 1].trim();
                    if (next.isEmpty() || next.startsWith("#")) continue;
                    if (pick == null || bw > 0 && Math.abs(bw - 1500000) < Math.abs(best - 1500000)) { best = bw; pick = next; }
                }
            } else {
                for (String line : lines) { line = line.trim(); if (!line.isEmpty() && !line.startsWith("#")) { pick = line; break; } }
            }
            if (pick == null) throw new IOException("empty playlist");
            url = java.net.URI.create(url).resolve(pick).toString();
            if (!master) return url;
        }
        return url;
    }

    static boolean playlist(String c, String url, String ref, String cookie) throws Exception {
        return Fetch.on(c, () -> {
            HttpURLConnection h = follow(url, ref, cookie, "bytes=0-15");
            if (h.getResponseCode() >= 400) return false;
            String type = String.valueOf(h.getContentType()).toLowerCase();
            byte[] b = h.getInputStream().readNBytes(16);
            h.disconnect();
            return type.contains("mpegurl") || new String(b, "ISO-8859-1").startsWith("#EXTM3U");
        });
    }

    static long[] startBytes(String c, String url, String ref, String cookie) throws Exception {
        long t = System.currentTimeMillis();
        long[] out = {-1, -1, 0};
        Fetch.on(c, () -> {
            HttpURLConnection h = follow(url, ref, cookie, "bytes=0-" + (START_BYTES - 1));
            int code = h.getResponseCode();
            if (code >= 400) throw new IOException("media HTTP " + code);
            out[0] = System.currentTimeMillis() - t;
            byte[] buf = new byte[65536];
            long total = 0;
            int r;
            try (InputStream in = h.getInputStream()) {
                while (total < START_BYTES && System.currentTimeMillis() - t < 40000 && (r = in.read(buf)) > 0) total += r;
            }
            out[1] = System.currentTimeMillis() - t;
            out[2] = total;
            return null;
        });
        return out;
    }

    static String clean(Throwable e) {
        Throwable x = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        String m = x.getClass().getSimpleName() + ":" + x.getMessage();
        m = m.replaceAll("https?://\\S+", "<url>").replace('\t', ' ').replace('\n', ' ');
        return m.length() > 140 ? m.substring(0, 140) : m;
    }

    public static void main(String[] a) throws Exception {
        boolean tor = !"direct".equals(System.getenv("NET"));
        String outFile = a[0];
        Set<String> only = new HashSet<>(Arrays.asList(a).subList(1, a.length));
        Set<String> done = new HashSet<>();
        if (Files.exists(Paths.get(outFile))) {
            for (String l : Files.readAllLines(Paths.get(outFile))) if (!l.startsWith("#") && l.contains("\t")) done.add(l.split("\t")[0]);
        }
        Fetch.setTorMode(tor);
        if (tor) {
            File dir = new File(System.getenv().getOrDefault("TORDIR", System.getenv("PREFIX") + "/tmp/orvid-tor")); dir.mkdirs();
            Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
            System.out.println("tor " + Tor.get().awaitReady(180000));
            Thread.sleep(3000);
        }
        com.orvid.ext.Registry.reload(new File(System.getenv("ROOT")));
        List<Source> all = new ArrayList<>(com.orvid.ui.Sources.current());
        System.out.println("sources " + all.size());
        StringBuilder ks = new StringBuilder();
        for (Source s : all) ks.append(s.key).append(s.adult ? "*" : "").append(' ');
        System.out.println(ks);
        PrintWriter out = new PrintWriter(new FileWriter(outFile, true), true);
        if (done.isEmpty()) out.println("#key\tadult\tsearch_ms\titems\tok/tries\tresolve_ms\tfirst_byte_ms\tstart_ms\topen_ms\tkind\terrors");
        ExecutorService pool = Executors.newFixedThreadPool(Integer.parseInt(System.getenv().getOrDefault("PAR", "3")));
        List<Future<?>> jobs = new ArrayList<>();
        for (Source s : all) {
            if (!only.isEmpty() && !only.contains(s.key)) continue;
            if (done.contains(s.key)) continue;
            jobs.add(pool.submit(() -> {
                StringBuilder row = new StringBuilder(s.key).append('\t').append(s.adult ? "A" : "G");
                List<String> errors = new ArrayList<>();
                List<StreamInfoItem> items = null;
                long t = System.currentTimeMillis();
                try {
                    String q = query(s);
                    var r = Hedge.call(Fetch.newCircuit("s" + s.key), () -> Fetch.newCircuit("s" + s.key), tor ? 2500 : 0,
                            c -> s.search(c, q, new com.orvid.search.Filters(), null).items);
                    items = r.value;
                } catch (Exception e) { errors.add("search " + clean(e)); }
                long searchMs = System.currentTimeMillis() - t;
                row.append('\t').append(items == null ? -1 : searchMs).append('\t').append(items == null ? -1 : items.size());
                if (items == null || items.isEmpty()) {
                    if (items != null) errors.add("no results");
                    out.println(row.append("\t0/0\t-\t-\t-\t-\t-\t").append(String.join(" | ", errors)));
                    return;
                }
                if (s instanceof com.orvid.ext.ExtSource && ((com.orvid.ext.ExtSource) s).webPlayback) {
                    out.println(row.append("\t-\t-\t-\t-\t-\tweb\tWebView playback"));
                    return;
                }
                int ok = 0, tries = 0;
                List<Long> res = new ArrayList<>(), fb = new ArrayList<>(), st = new ArrayList<>(), open = new ArrayList<>();
                String kind = "-";
                for (StreamInfoItem it : items) {
                    if (tries >= TRIES) break;
                    tries++;
                    long t0 = System.currentTimeMillis();
                    try {
                        Resolve.Stream r = Resolve.preview(it.getUrl());
                        if (r == null || r.direct == null) throw new IOException("no resolver");
                        long resolveMs = System.currentTimeMillis() - t0;
                        boolean hls = r.direct.hls || playlist(r.circuit, r.direct.url, r.direct.referer, r.direct.cookie);
                        String media = mediaUrl(r.circuit, r.direct.url, hls, r.direct.referer, r.direct.cookie);
                        long[] b = startBytes(r.circuit, media, r.direct.referer, r.direct.cookie);
                        if (b[2] < 16 * 1024 && !r.direct.hls) throw new IOException("short media " + b[2]);
                        ok++;
                        res.add(resolveMs); fb.add(b[0]); st.add(b[1]);
                        open.add(System.currentTimeMillis() - t0);
                        kind = (hls ? (r.direct.hls ? "HLS " : "HLS* ") : "MP4 ") + java.net.URI.create(media).getHost();
                    } catch (Exception e) { errors.add(clean(e)); }
                }
                row.append('\t').append(ok).append('/').append(tries)
                        .append('\t').append(med(res)).append('\t').append(med(fb)).append('\t').append(med(st)).append('\t').append(med(open))
                        .append('\t').append(kind).append('\t').append(String.join(" | ", errors));
                out.println(row);
            }));
        }
        for (Future<?> f : jobs) { try { f.get(300, TimeUnit.SECONDS); } catch (Exception ex) { f.cancel(true); } }
        out.println("#done " + new Date());
        if (tor) Tor.get().stop();
        Thread.sleep(300);
        System.exit(0);
    }

    static String med(List<Long> v) {
        if (v.isEmpty()) return "-";
        List<Long> s = new ArrayList<>(v);
        Collections.sort(s);
        return Long.toString(s.get(s.size() / 2));
    }
}
