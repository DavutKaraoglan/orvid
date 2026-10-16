import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.play.*;
import java.io.File;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
public class StartProbe {
    static String fetchText(String c, String url, String ref) throws Exception {
        return Fetch.on(c, () -> {
            HttpURLConnection h = Fetch.open(url);
            h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
            if (ref != null) h.setRequestProperty("Referer", ref);
            return new String(h.getInputStream().readAllBytes());
        });
    }
    static int firstBytes(String c, String url, String ref) throws Exception {
        return Fetch.on(c, () -> {
            HttpURLConnection h = Fetch.open(url);
            h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
            h.setRequestProperty("Accept-Encoding", "identity");
            h.setRequestProperty("Range", "bytes=0-262143");
            if (ref != null) h.setRequestProperty("Referer", ref);
            int code = h.getResponseCode();
            if (code >= 400) throw new java.io.IOException("HTTP " + code);
            return h.getInputStream().readAllBytes().length;
        });
    }
    static String media(String c, Direct d) throws Exception {
        if (!d.hls) return d.url;
        String url = d.url;
        for (int depth = 0; depth < 3; depth++) {
            String pl = fetchText(c, url, d.referer);
            String pick = null;
            for (String line : pl.split("\n")) { line = line.trim(); if (!line.isEmpty() && !line.startsWith("#")) { pick = line; break; } }
            if (pick == null) return null;
            url = java.net.URI.create(url).resolve(pick).toString();
            if (!pl.contains("#EXT-X-STREAM-INF")) return url;
        }
        return url;
    }
    interface Search { List<StreamInfoItem> run(String c) throws Exception; }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        Map<String, Search> all = new LinkedHashMap<>();
        Set<String> want = new HashSet<>(Arrays.asList(a));
        for (com.orvid.adult.HtmlTube t : com.orvid.adult.Tubes.ALL) if (!t.web) all.put("tube" + t.serviceId, c -> t.search(c, "milf", null).items);
        all.put("xvideos", c -> com.orvid.xv.Xvideos.search(c, "milf", null).items);
        all.put("pornhub", c -> com.orvid.ay.Aylo.search(c, "milf", null).items);
        all.put("eporner", c -> com.orvid.ep.Eporner.search(c, "milf", null).items);
        all.put("xhamster", c -> com.orvid.xh.XHamster.search(c, "milf", null).items);
        all.put("tnaflix", c -> com.orvid.tn.Tnaflix.search(c, "milf", null).items);
        all.put("txxx", c -> com.orvid.adult.Txxx.search(c, "milf", null).items);
        all.put("twitch", c -> com.orvid.tw.Twitch.search(c, "minecraft", null).items);
        all.put("archive", c -> com.orvid.ia.Archive.search(c, "apollo", null).items);
        for (com.orvid.more.Site s : com.orvid.more.Sites.ALL) all.put(s.name, c -> s.search(c, s.name.equals("NASA") ? "mars" : "cat", null).items);
        com.orvid.yt.Visionos.refill();
        Thread.sleep(3000);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<String>> out = new ArrayList<>();
        for (Map.Entry<String, Search> e : all.entrySet()) {
            if (!want.isEmpty() && !want.contains(e.getKey())) continue;
            out.add(pool.submit(() -> {
                String c0 = Fetch.newCircuit("s" + e.getKey());
                try {
                    List<StreamInfoItem> items = Fetch.retrying(c0, cc -> e.getValue().run(cc));
                    String url = items.get(0).getUrl();
                    long t0 = System.currentTimeMillis();
                    Resolve.Stream st = Resolve.preview(url);
                    long t1 = System.currentTimeMillis();
                    if (st == null) return e.getKey() + ": no resolver";
                    String m = media(st.circuit, st.direct.url == null ? null : st.direct);
                    long t2 = System.currentTimeMillis();
                    int n = firstBytes(st.circuit, m, st.direct.referer);
                    long t3 = System.currentTimeMillis();
                    return String.format("%-18s resolve %5dms  playlist %5dms  first256KB %5dms  TOTAL %5dms %s %s", e.getKey(), t1 - t0, t2 - t1, t3 - t2, t3 - t0,
                            st.direct.hls ? "HLS" : "MP4", java.net.URI.create(m).getHost());
                } catch (Exception ex) { return e.getKey() + ": FAIL " + ex; }
            }));
        }
        for (Future<String> f : out) System.out.println(f.get());
        Tor.get().stop(); Thread.sleep(500); System.exit(0);
    }
}
