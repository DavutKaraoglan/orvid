import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.search.*;
import com.orvid.yt.Visionos;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
public class OrderProbe {
    static String smallest(StreamInfoItem it) {
        String u = com.orvid.ui.Ui.thumbnail(it.getThumbnails(), 120);
        if (u == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("i\\.ytimg\\.com/vi(?:_webp)?/([\\w-]{11})/").matcher(u);
        if (m.find()) return "https://i.ytimg.com/vi/" + m.group(1) + "/default.jpg";
        return u.replace("/big_", "/small_").replaceFirst("type=o500x280[^&]*", "type=w320").replace("/medium@2x/", "/medium/");
    }
    static String normal(StreamInfoItem it) {
        String u = com.orvid.ui.Ui.thumbnail(it.getThumbnails(), 360);
        if (u == null) return null;
        if (u.contains("i.ytimg.com/")) { u = u.replace("/hqdefault.webp", "/mqdefault.webp").replace("/hq720.webp", "/mqdefault.webp"); int q = u.indexOf('?'); if (q > 0) u = u.substring(0, q); }
        return u.replace("/big_", "/small_").replaceFirst("type=o500x280[^&]*", "type=w320").replace("/medium@2x/", "/medium/");
    }
    static String prefix;
    static void get(String url, String ref) throws Exception {
        String host = java.net.URI.create(url).getHost();
        Fetch.on(prefix + Math.floorMod(host.hashCode(), 3), () -> {
            HttpURLConnection h = Fetch.open(url);
            h.setConnectTimeout(12000); h.setReadTimeout(15000);
            h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
            if (ref != null) h.setRequestProperty("Referer", ref);
            if (h.getResponseCode() >= 400) throw new IOException("HTTP " + h.getResponseCode());
            try (InputStream in = h.getInputStream()) { in.readAllBytes(); }
            return null;
        });
    }
    static List<StreamInfoItem> top(String text) throws Exception {
        Query q = Query.parse(text);
        Filters f = new Filters();
        for (Source s : com.orvid.ui.Sources.EXTRA) f.set(s, true);
        Set<String> web = new HashSet<>(Arrays.asList("spankbang", "fourkporn", "jable", "missav", "ashemaletube", "boyfriendtv"));
        List<Source> srcs = new ArrayList<>();
        for (Source s : com.orvid.ui.Sources.EXTRA) if (!web.contains(s.key) && s.wanted(f, q)) srcs.add(s);
        List<List<StreamInfoItem>> lists = new ArrayList<>();
        for (int i = 0; i <= srcs.size(); i++) lists.add(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<?>> fs = new ArrayList<>();
        fs.add(pool.submit(() -> { try { lists.set(0, com.orvid.yt.Search.first(Visionos.takeCircuit(), q.platformQuery, f.youtubeParams()).items); } catch (Exception e) {} }));
        for (int i = 0; i < srcs.size(); i++) { final int k = i + 1; final Source s = srcs.get(i);
            fs.add(pool.submit(() -> { try { lists.set(k, Hedge.call(Fetch.newCircuit("x"), () -> Fetch.newCircuit("x"), 2500, c -> s.search(c, q.platformQuery, f, null)).value.items); } catch (Exception e) {} })); }
        for (Future<?> x : fs) try { x.get(25, TimeUnit.SECONDS); } catch (Exception e) { x.cancel(true); }
        pool.shutdownNow();
        double[] w = new double[lists.size()]; Arrays.fill(w, 1.0); w[0] = 2.0;
        List<StreamInfoItem> r = f.arrange(q, lists, w, null);
        return r.subList(0, Math.min(24, r.size()));
    }
    static String run(String strategy, boolean small, String text, int round) throws Exception {
        List<StreamInfoItem> items = top(text);
        prefix = "ord" + round + strategy + small + "-";
        int n = items.size();
        long[] done = new long[n];
        int threads = strategy.equals("seq") ? 1 : 12;
        AtomicLong seq = new AtomicLong();
        PriorityBlockingQueue<Runnable> queue = new PriorityBlockingQueue<>(64, (x, y) -> 0);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 5, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>() {
            public boolean offer(Runnable r) { return strategy.equals("lifo") ? super.offerFirst(r) : super.offerLast(r); }
        });
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            final int k = i; StreamInfoItem it = items.get(i);
            String url = small ? smallest(it) : normal(it);
            String ref = it.getUrl().replaceAll("^(https?://[^/]+/).*", "$1");
            pool.execute(() -> { try { get(url, url.contains("ytimg") ? null : ref); done[k] = System.currentTimeMillis() - t0; } catch (Exception e) { done[k] = -1; } });
        }
        pool.shutdown(); pool.awaitTermination(120, TimeUnit.SECONDS);
        long first = Long.MAX_VALUE; for (long d : done) if (d > 0) first = Math.min(first, d);
        return String.format("%-5s %-6s q=%-6s first=%.1fs first_row=%s first_screen=%s all=%s", strategy, small ? "small" : "normal", text, first / 1000.0, until(done, 3), until(done, 12), until(done, n));
    }
    static String until(long[] d, int k) {
        long m = 0; for (int i = 0; i < Math.min(k, d.length); i++) { if (d[i] < 0) return "fail"; m = Math.max(m, d[i]); }
        return String.format("%.1fs", m / 1000.0);
    }
    public static void main(String[] a) throws Exception {
        System.setProperty("http.maxConnections", "48");
        System.setProperty("http.keepAliveDuration", "300000");
        Fetch.setTorMode(true);
        Source.adultUnlocked = false;
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        Tor.get().awaitReady(180000);
        Visionos.refill(); Thread.sleep(5000);
        String[][] plan = {{"lifo", "0", "cat"}, {"fifo", "0", "dog"}, {"seq", "0", "car"}, {"fifo", "1", "rain"}, {"seq", "1", "tree"},
                           {"seq", "1", "bird"}, {"fifo", "1", "fish"}, {"seq", "0", "moon"}, {"fifo", "0", "snow"}, {"lifo", "0", "lake"}};
        int round = 0;
        for (String[] p : plan) { System.out.println(run(p[0], p[1].equals("1"), p[2], round++)); }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
