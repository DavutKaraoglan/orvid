import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.search.*;
import com.orvid.yt.Visionos;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class ThumbFlowProbe {
    static int circuits;
    static String prefix;
    static String circuitFor(String url) {
        String host = java.net.URI.create(url).getHost();
        return prefix + Math.floorMod(host == null ? 0 : host.hashCode(), circuits);
    }
    static void fetch(String url, String ref, boolean head) throws Exception {
        Fetch.on(circuitFor(url), () -> {
            HttpURLConnection h = Fetch.open(url);
            h.setConnectTimeout(12000); h.setReadTimeout(15000);
            h.setInstanceFollowRedirects(!head);
            if (head) h.setRequestMethod("HEAD");
            h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
            if (ref != null) h.setRequestProperty("Referer", ref);
            int code = h.getResponseCode();
            if (!head && code >= 400) throw new IOException("HTTP " + code);
            try (InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream()) { if (in != null) in.readAllBytes(); }
            return null;
        });
    }
    static final class Job implements Runnable, Comparable<Job> {
        static final AtomicLong SEQ = new AtomicLong();
        final long seq = SEQ.incrementAndGet(); final Runnable r;
        Job(Runnable r) { this.r = r; }
        public void run() { r.run(); }
        public int compareTo(Job o) { return Long.compare(o.seq, seq); }
    }
    public static void main(String[] a) throws Exception {
        System.setProperty("http.maxConnections", "48");
        System.setProperty("http.keepAliveDuration", "300000");
        int threads = Integer.parseInt(a[0]); circuits = Integer.parseInt(a[1]); boolean warm = a[2].equals("1"); String q = a[3];
        prefix = "img" + threads + "x" + circuits + "-";
        Fetch.setTorMode(true);
        Source.adultUnlocked = true;
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        Tor.get().awaitReady(180000);
        Visionos.refill(); Thread.sleep(5000);
        Map<String, String> seeds = new HashMap<>();
        for (String l : java.nio.file.Files.readAllLines(new File(System.getenv("TMPDIR") + "/seeds.txt").toPath())) { String[] p = l.split("="); seeds.put(p[0], p[1]); }
        seeds.put("youtube", "i.ytimg.com");
        Filters f = new Filters();
        for (Source s : com.orvid.ui.Sources.EXTRA) f.set(s, true);
        Set<String> web = new HashSet<>(Arrays.asList("spankbang", "fourkporn", "jable", "missav", "ashemaletube", "boyfriendtv"));
        ThreadPoolExecutor images = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new PriorityBlockingQueue<Runnable>());
        ExecutorService warmer = Executors.newCachedThreadPool();
        ExecutorService search = Executors.newFixedThreadPool(16);
        long t0 = System.currentTimeMillis();
        List<Long> delay = Collections.synchronizedList(new ArrayList<>());
        List<Long> absolute = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger fails = new AtomicInteger(), queued = new AtomicInteger();
        List<Future<?>> searches = new ArrayList<>();
        List<String[]> plan = new ArrayList<>();
        plan.add(new String[]{"youtube"});
        for (Source s : com.orvid.ui.Sources.EXTRA) if (!web.contains(s.key) && s.wanted(f, Query.parse(q))) plan.add(new String[]{s.key});
        for (String[] p : plan) {
            String key = p[0];
            if (warm && seeds.containsKey(key)) {
                String host = seeds.get(key);
                for (int i = 0; i < 2; i++) warmer.execute(() -> { try { fetch("https://" + host + "/", null, true); } catch (Exception ignored) {} });
            }
            searches.add(search.submit(() -> {
                List<org.schabi.newpipe.extractor.stream.StreamInfoItem> items;
                try {
                    if (key.equals("youtube")) items = Hedge.call(Visionos.takeCircuit(), Visionos::takeCircuit, 1500, c -> com.orvid.yt.Search.first(c, q, f.youtubeParams())).value.items;
                    else { Source s = null; for (Source x : com.orvid.ui.Sources.EXTRA) if (x.key.equals(key)) s = x; final Source src = s;
                        items = Hedge.call(Fetch.newCircuit("x"), () -> Fetch.newCircuit("x"), 2500, c -> src.search(c, q, f, null)).value.items; }
                } catch (Exception e) { return; }
                long arrived = System.currentTimeMillis();
                for (int i = 0; i < Math.min(8, items.size()); i++) {
                    var it = items.get(i);
                    String url = com.orvid.ui.Ui.thumbnail(it.getThumbnails(), 360);
                    if (url == null) continue;
                    String ref = it.getUrl().replaceAll("^(https?://[^/]+/).*", "$1");
                    queued.incrementAndGet();
                    images.execute(new Job(() -> {
                        try { fetch(url, key.equals("youtube") ? null : ref, false); long now = System.currentTimeMillis(); delay.add(now - arrived); absolute.add(now - t0); }
                        catch (Exception e) { fails.incrementAndGet(); }
                    }));
                }
            }));
        }
        for (Future<?> s : searches) try { s.get(150, TimeUnit.SECONDS); } catch (Exception e) {}
        images.shutdown(); images.awaitTermination(120, TimeUnit.SECONDS);
        Collections.sort(delay); Collections.sort(absolute);
        int n = delay.size();
        System.out.printf("threads=%d circuits=%d warm=%s q=%-5s thumbs=%d/%d failed=%d | after results: median %.1fs p90 %.1fs | since search p90 %.1fs all %.1fs%n",
                threads, circuits, warm ? "yes" : "no", q, n, queued.get(), fails.get(), delay.get(n / 2) / 1000.0, delay.get(n * 9 / 10) / 1000.0, absolute.get(n * 9 / 10) / 1000.0, absolute.get(n - 1) / 1000.0);
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
