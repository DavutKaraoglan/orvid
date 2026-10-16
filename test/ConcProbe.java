import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.search.*;
import com.orvid.yt.Visionos;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
public class ConcProbe {
    interface Job { int run() throws Exception; }
    static final class Q implements Runnable, Comparable<Q> {
        static long seq; final int p; final double tie; final long order = seq++; final Runnable r;
        Q(int p, double tie, Runnable r) { this.p = p; this.tie = tie; this.r = r; }
        public void run() { r.run(); }
        public int compareTo(Q o) { if (p != o.p) return Integer.compare(p, o.p); if (tie != o.tie) return Double.compare(tie, o.tie); return Long.compare(order, o.order); }
    }
    static int extra(Source s, Query q, Filters f) throws Exception {
        var r = Hedge.call(Fetch.newCircuit("x"), () -> Fetch.newCircuit("x"), 2500, c -> s.search(c, q.platformQuery, f, null));
        if (r.value.items.isEmpty()) {
            var again = Hedge.call(Fetch.newCircuit("x"), () -> Fetch.newCircuit("x"), 2500, c -> s.search(c, q.platformQuery, f, null));
            if (!again.value.items.isEmpty()) r = again;
        }
        return r.value.items.size();
    }
    static String run(int k, String text, long capMs) throws Exception {
        Query q = Query.parse(text);
        Filters f = new Filters();
        for (Source s : com.orvid.ui.Sources.EXTRA) f.set(s, true);
        Source.adultUnlocked = ADULT;
        ThreadPoolExecutor pool = new ThreadPoolExecutor(k, k, 5, TimeUnit.SECONDS, new PriorityBlockingQueue<Runnable>());
        long t0 = System.currentTimeMillis();
        Map<String, Long> done = new ConcurrentHashMap<>();
        Map<String, Integer> count = new ConcurrentHashMap<>();
        List<Q> jobs = new ArrayList<>();
        Map<String, Job> all = new LinkedHashMap<>();
        all.put("youtube", () -> Hedge.call(Visionos.takeCircuit(), Visionos::takeCircuit, 1500, c -> com.orvid.yt.Search.first(c, q.platformQuery, f.youtubeParams())).value.items.size());
        all.put("dailymotion", () -> com.orvid.dm.Dailymotion.search(Visionos.takeCircuit(), q.platformQuery, 1, f.dailymotionParams(), false).items.size());
        int[] prio = {0, 1};
        int idx = 0;
        CountDownLatch latch;
        List<Object[]> spec = new ArrayList<>();
        spec.add(new Object[]{"youtube", 0, 0.0});
        spec.add(new Object[]{"dailymotion", 1, 0.0});
        for (Source s : com.orvid.ui.Sources.EXTRA) {
            if (!s.wanted(f, q)) continue;
            all.put(s.key, () -> extra(s, q, f));
            spec.add(new Object[]{s.key, s.priority(q), -s.weight});
        }
        latch = new CountDownLatch(spec.size());
        final CountDownLatch l = latch;
        for (Object[] sp : spec) {
            String key = (String) sp[0];
            jobs.add(new Q((Integer) sp[1], (Double) sp[2], () -> {
                try { count.put(key, all.get(key).run()); } catch (Exception e) { count.put(key, -1); }
                done.put(key, System.currentTimeMillis() - t0);
                l.countDown();
            }));
        }
        Collections.sort(jobs);
        for (Q j : jobs) pool.execute(j);
        latch.await(capMs, TimeUnit.MILLISECONDS);
        pool.shutdownNow();
        List<Long> ok = new ArrayList<>();
        long firstNonEmpty = -1;
        for (var e : done.entrySet()) if (count.getOrDefault(e.getKey(), -1) > 0) ok.add(e.getValue());
        Collections.sort(ok);
        int total = spec.size();
        String yt = done.containsKey("youtube") ? done.get("youtube") + "" : "-";
        String p = ok.isEmpty() ? "-" : ok.get(0) + "";
        return String.format("slots=%d q=%-5s sources=%d with_results=%d | first=%sms youtube=%sms half=%s p90=%s all=%s | unfinished=%d",
                k, text, total, ok.size(), p, yt, at(ok, total / 2), at(ok, total * 9 / 10), at(ok, ok.size()), total - done.size());
    }
    static boolean ADULT;
    static String at(List<Long> ok, int n) { return n <= 0 || n > ok.size() ? "-" : ok.get(n - 1) / 1000.0 + "s"; }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        ADULT = a.length > 1;
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        Visionos.refill(); Thread.sleep(5000);
        Object[][] plan = a.length > 0 && a[0].equals("big") ? new Object[][]{{12, "lake"}, {6, "road"}, {18, "city"}, {12, "baby"}, {6, "ship"}, {18, "game"}, {24, "song"}, {12, "park"}} : new Object[][]{{6, "cat"}, {2, "dog"}, {3, "rain"}, {1, "bird"}, {6, "car"}, {3, "tree"}, {2, "fish"}, {12, "snow"}, {1, "moon"}};
        for (Object[] p : plan) {
            System.out.println(run((Integer) p[0], (String) p[1], 150000));
            Thread.sleep(3000);
        }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
