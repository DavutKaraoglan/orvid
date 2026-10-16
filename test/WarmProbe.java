import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;
public class WarmProbe {
    static byte[] get(String c, String u) throws Exception {
        return Fetch.on(c, () -> { HttpURLConnection h = Fetch.open(u); h.setRequestProperty("User-Agent", Fetch.USER_AGENT); try (var in = h.getInputStream()) { return in.readAllBytes(); } });
    }
    static long wave(String c, List<String> urls, int threads) throws Exception {
        ExecutorService p = Executors.newFixedThreadPool(threads);
        long t = System.currentTimeMillis();
        List<Future<?>> fs = new ArrayList<>();
        List<Long> each = Collections.synchronizedList(new ArrayList<>());
        for (String u : urls) fs.add(p.submit(() -> { get(c, u); each.add(System.currentTimeMillis() - t); return null; }));
        for (Future<?> f : fs) try { f.get(); } catch (Exception e) {}
        p.shutdown();
        Collections.sort(each);
        System.out.println("   first " + each.get(0) + "ms, half " + each.get(each.size() / 2) + "ms, all " + each.get(each.size() - 1) + "ms");
        return 0;
    }
    public static void main(String[] a) throws Exception {
        System.setProperty("http.maxConnections", "48");
        System.setProperty("http.keepAliveDuration", "300000");
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        List<String> ids = new ArrayList<>();
        for (String q : new String[]{"cat", "dog", "car", "food", "music", "news"}) {
            var items = com.orvid.yt.Search.first(Fetch.newCircuit("s"), q, null).items;
            for (var it : items) { String u = it.getUrl(); int i = u.indexOf("v="); if (i > 0) ids.add(u.substring(i + 2, i + 13)); }
        }
        Collections.shuffle(ids);
        for (int round = 0; round < 2; round++) {
            List<String> cold = new ArrayList<>(), warm = new ArrayList<>();
            for (int i = 0; i < 24; i++) cold.add("https://i.ytimg.com/vi/" + ids.get(round * 48 + i) + "/default.jpg");
            for (int i = 24; i < 48; i++) warm.add("https://i.ytimg.com/vi/" + ids.get(round * 48 + i) + "/default.jpg");
            System.out.println("round " + round + " cold (new circuit, no connection):");
            wave(Fetch.newCircuit("cold"), cold, 12);
            String c = Fetch.newCircuit("warm");
            ExecutorService w = Executors.newCachedThreadPool();
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 12; i++) fs.add(w.submit(() -> get(c, "https://i.ytimg.com/vi/jNQXAC9IVRw/default.jpg")));
            for (Future<?> f : fs) try { f.get(); } catch (Exception e) {}
            w.shutdown();
            System.out.println("round " + round + " warmed (12 connections ready):");
            wave(c, warm, 12);
        }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
