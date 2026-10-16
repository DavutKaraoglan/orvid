import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.search.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;
public class ThumbHostProbe {
    static long[] get(String c, String u, String ref, String[] conn) throws Exception {
        long t = System.currentTimeMillis();
        int n = Fetch.on(c, () -> {
            HttpURLConnection h = Fetch.open(u);
            h.setConnectTimeout(12000); h.setReadTimeout(15000);
            h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
            h.setRequestProperty("Accept", "image/webp,image/jpeg,image/png,image/*;q=0.8");
            if (ref != null) h.setRequestProperty("Referer", ref);
            int code = h.getResponseCode();
            conn[0] = String.valueOf(h.getHeaderField("Connection"));
            conn[1] = String.valueOf(h.getHeaderField("Server"));
            if (code >= 400) throw new IOException("HTTP " + code);
            try (InputStream in = h.getInputStream()) { return in.readAllBytes().length; }
        });
        return new long[]{System.currentTimeMillis() - t, n};
    }
    public static void main(String[] a) throws Exception {
        System.setProperty("http.maxConnections", "48");
        System.setProperty("http.keepAliveDuration", "300000");
        Fetch.setTorMode(true);
        Source.adultUnlocked = true;
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        Filters f = new Filters();
        for (Source s : com.orvid.ui.Sources.EXTRA) f.set(s, true);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<String>> rows = new ArrayList<>();
        for (Source s : com.orvid.ui.Sources.EXTRA) {
            rows.add(pool.submit(() -> {
                String q = BenchProbe.query(s);
                try {
                    var items = Hedge.call(Fetch.newCircuit("s"), () -> Fetch.newCircuit("s"), 2500, c -> s.search(c, q, f, null)).value.items;
                    List<String> urls = new ArrayList<>();
                    for (var it : items) if (!it.getThumbnails().isEmpty()) urls.add(it.getThumbnails().get(0).getUrl());
                    if (urls.isEmpty()) return s.key + "\tno thumbnails";
                    String ref = items.get(0).getUrl().replaceAll("^(https?://[^/]+/).*", "$1");
                    String c = Fetch.newCircuit("img");
                    String[] conn = new String[2];
                    Set<String> hosts = new TreeSet<>();
                    long cold = -1, warmSum = 0, bytes = 0; int warmN = 0, fails = 0;
                    for (int i = 0; i < Math.min(6, urls.size()); i++) {
                        hosts.add(java.net.URI.create(urls.get(i)).getHost());
                        try {
                            long[] r = get(c, urls.get(i), ref, conn);
                            bytes += r[1];
                            if (cold < 0) cold = r[0]; else { warmSum += r[0]; warmN++; }
                        } catch (Exception e) { fails++; }
                    }
                    int ok = (cold >= 0 ? 1 : 0) + warmN;
                    return s.key + "\t" + cold + "\t" + (warmN > 0 ? warmSum / warmN : -1) + "\t" + (ok > 0 ? bytes / ok / 1024 : 0) + "KB\t" + fails + "\t" + conn[0] + "\t" + conn[1] + "\t" + hosts;
                } catch (Exception e) { return s.key + "\tFAIL " + e.getMessage(); }
            }));
        }
        System.out.println("source\tcold_ms\twarm_ms\tsize\tfailed\tConnection\tServer\thosts");
        for (Future<String> r : rows) { try { System.out.println(r.get(180, TimeUnit.SECONDS)); } catch (Exception e) { System.out.println("? timeout"); } }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
