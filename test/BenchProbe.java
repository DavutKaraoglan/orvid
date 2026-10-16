import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.play.*;
import com.orvid.search.Source;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
public class BenchProbe {
    interface Search { List<StreamInfoItem> run(String c) throws Exception; }
    interface Media { String[] run(String c, String url) throws Exception; }
    static final Set<String> WEB = new HashSet<>(Arrays.asList("spankbang", "fourkporn", "jable", "missav", "ashemaletube", "boyfriendtv"));
    static final Set<String> WEB_PLAY = new HashSet<>(Arrays.asList("drtuber", "porndoe", "thisvid"));
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
        }
        if (s.adult) return s.orientation == Source.GAY ? "muscle" : s.orientation == Source.TRANS ? "blonde" : "milf";
        return "cat";
    }
    static long firstBytes(String c, String url, String ref) throws Exception {
        long t = System.currentTimeMillis();
        Fetch.on(c, () -> {
            HttpURLConnection h = Fetch.open(url);
            h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
            h.setRequestProperty("Accept-Encoding", "identity");
            h.setRequestProperty("Range", "bytes=0-262143");
            if (ref != null) h.setRequestProperty("Referer", ref);
            int code = h.getResponseCode();
            if (code >= 400) throw new IOException("media HTTP " + code);
            return h.getInputStream().readAllBytes().length;
        });
        return System.currentTimeMillis() - t;
    }
    static String mediaUrl(String c, String url, boolean hls, String ref) throws Exception {
        if (!hls) return url;
        for (int depth = 0; depth < 3; depth++) {
            final String u = url;
            String pl = Fetch.on(c, () -> {
                HttpURLConnection h = Fetch.open(u);
                h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
                if (ref != null) h.setRequestProperty("Referer", ref);
                return new String(h.getInputStream().readAllBytes());
            });
            String pick = null;
            for (String line : pl.split("\n")) { line = line.trim(); if (!line.isEmpty() && !line.startsWith("#")) { pick = line; break; } }
            if (pick == null) return null;
            url = java.net.URI.create(url).resolve(pick).toString();
            if (!pl.contains("#EXT-X-STREAM-INF")) return url;
        }
        return url;
    }
    static int thumbs(String c, List<StreamInfoItem> items) {
        int ok = 0, n = 0;
        for (StreamInfoItem it : items) {
            if (it.getThumbnails().isEmpty()) continue;
            if (++n > 4) break;
            String u = it.getThumbnails().get(0).getUrl();
            String page = it.getUrl().replaceAll("^(https?://[^/]+/).*", "$1");
            try {
                int code = Fetch.on(c, () -> {
                    HttpURLConnection h = Fetch.open(u);
                    h.setConnectTimeout(12000); h.setReadTimeout(15000);
                    h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
                    h.setRequestProperty("Referer", page);
                    int cd = h.getResponseCode();
                    if (cd < 400) h.getInputStream().readAllBytes();
                    return cd;
                });
                if (code < 400) ok++;
            } catch (Exception ignored) {}
        }
        return n == 0 ? -1 : ok * 100 / Math.min(n, 4);
    }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        com.orvid.yt.Visionos.refill();
        Thread.sleep(4000);
        PrintWriter out = new PrintWriter(new FileWriter(a[0], true), true);
        Map<String, Search> searches = new LinkedHashMap<>();
        Map<String, Media> resolvers = new HashMap<>();
        searches.put("youtube", c -> com.orvid.yt.Search.first(c, "cat", null).items);
        searches.put("dailymotion", c -> com.orvid.dm.Dailymotion.search(c, "cat", 1, new com.orvid.search.Filters().dailymotionParams()).items);
        resolvers.put("dailymotion", (c, url) -> new String[]{com.orvid.dm.Dailymotion.resolve(c, com.orvid.dm.Dailymotion.videoId(url)).hls, "hls", null});
        searches.put("vk", c -> com.orvid.vk.Vk.search(c, "cat", null).items);
        resolvers.put("vk", (c, url) -> { var v = com.orvid.vk.Vk.fetch(c, com.orvid.vk.Vk.videoId(url)); String u = v.files.values().iterator().next(); return new String[]{u, u.contains("m3u8") ? "hls" : "mp4", null}; });
        Map<String, Source> byKey = new HashMap<>();
        for (Source s : com.orvid.ui.Sources.EXTRA) {
            if (WEB.contains(s.key)) continue;
            byKey.put(s.key, s);
            String q = query(s);
            searches.put(s.key, c -> s.search(c, q, new com.orvid.search.Filters(), null).items);
        }
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<?>> all = new ArrayList<>();
        for (Map.Entry<String, Search> e : searches.entrySet()) {
            if (a.length > 1 && !Arrays.asList(a).subList(1, a.length).contains(e.getKey())) continue;
            all.add(pool.submit(() -> {
                String key = e.getKey();
                StringBuilder row = new StringBuilder(key);
                long[] st = new long[2];
                List<StreamInfoItem> items = null;
                String err = "";
                for (int i = 0; i < 2; i++) {
                    long t = System.currentTimeMillis();
                    try {
                        var r = Hedge.call(Fetch.newCircuit("b" + key), () -> Fetch.newCircuit("b" + key), 2500, e.getValue()::run);
                        st[i] = System.currentTimeMillis() - t;
                        if (items == null) items = r.value;
                    } catch (Exception ex) { st[i] = -1; err = ex.getClass().getSimpleName() + ":" + ex.getMessage(); }
                }
                row.append('\t').append(st[0]).append('\t').append(st[1]).append('\t').append(items == null ? -1 : items.size());
                String c = Fetch.newCircuit("m" + key);
                if (items == null || items.isEmpty()) { out.println(row.append("\t-\t-\t-\t-\t").append(err.isEmpty() ? "no results" : err)); return; }
                row.append('\t').append(thumbs(c, items));
                if (WEB_PLAY.contains(key)) { out.println(row.append("\tweb\t-\t-\tWebView playback")); return; }
                try {
                    String url = items.get(0).getUrl();
                    long t = System.currentTimeMillis();
                    String m; boolean hls; String ref; String mc = c;
                    if (resolvers.containsKey(key)) { String[] r = resolvers.get(key).run(c, url); m = r[0]; hls = r[1].equals("hls"); ref = r[2]; }
                    else { Resolve.Stream s = Resolve.preview(url); if (s == null) throw new IOException("no resolver"); m = s.direct.url; hls = s.direct.hls; ref = s.direct.referer; mc = s.circuit; }
                    long resolveMs = System.currentTimeMillis() - t;
                    String media = mediaUrl(mc, m, hls, ref);
                    long fb = firstBytes(mc, media, ref);
                    out.println(row.append('\t').append(resolveMs).append('\t').append(fb).append('\t').append(hls ? "HLS" : "MP4").append('\t').append(java.net.URI.create(media).getHost()));
                } catch (Exception ex) {
                    out.println(row.append("\t-\t-\t-\t").append(ex.getClass().getSimpleName()).append(':').append(String.valueOf(ex.getMessage()).replace('\t', ' ')));
                }
            }));
        }
        for (Future<?> f : all) { try { f.get(240, TimeUnit.SECONDS); } catch (Exception ex) { f.cancel(true); } }
        out.println("#done");
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
