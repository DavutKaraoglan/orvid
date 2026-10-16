import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
public class PerfProbe {
    interface S { int run(String c, String q) throws Exception; }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        long w0 = System.currentTimeMillis();
        List<Thread> warm = new ArrayList<>();
        for (int port : Tor.get().ports()) {
            Thread t = new Thread(() -> { try { java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://check.torproject.org/api/ip").openConnection(new java.net.Proxy(java.net.Proxy.Type.HTTP, new java.net.InetSocketAddress("127.0.0.1", port))); c.setConnectTimeout(15000); c.setReadTimeout(15000); c.getInputStream().readAllBytes(); } catch (Exception ignored) {} });
            t.start(); warm.add(t);
        }
        for (Thread t : warm) t.join();
        System.out.println("warmed " + warm.size() + " ports in " + (System.currentTimeMillis() - w0) + "ms");
        Map<String, S> src = new LinkedHashMap<>();
        src.put("youtube", (c, q) -> com.orvid.yt.Search.first(c, q, null).items.size());
        src.put("dailymotion", (c, q) -> com.orvid.dm.Dailymotion.search(c, q, 1, "&sort=relevance&family_filter=false&longer_than=1", false).items.size());
        src.put("vk", (c, q) -> com.orvid.vk.Vk.search(c, q, null).items.size());
        src.put("twitch", (c, q) -> com.orvid.tw.Twitch.search(c, q, null).items.size());
        src.put("peertube", (c, q) -> com.orvid.pt.PeerTube.search(c, q, null, false).items.size());
        src.put("archive", (c, q) -> com.orvid.ia.Archive.search(c, q, null).items.size());
        src.put("xvideos", (c, q) -> com.orvid.xv.Xvideos.search(c, q, null).items.size());
        src.put("pornhub", (c, q) -> com.orvid.ay.Aylo.search(c, q, null).items.size());
        src.put("eporner", (c, q) -> com.orvid.ep.Eporner.search(c, q, null).items.size());
        src.put("xhamster", (c, q) -> com.orvid.xh.XHamster.search(c, q, null).items.size());
        src.put("tnaflix", (c, q) -> com.orvid.tn.Tnaflix.search(c, q, null).items.size());
        src.put("txxx", (c, q) -> com.orvid.adult.Txxx.search(c, q, null).items.size());
        src.put("redgifs", (c, q) -> com.orvid.adult.RedGifs.search(c, q, null).items.size());
        src.put("pornone", (c, q) -> com.orvid.adult.PornOne.search(c, q, null).items.size());
        for (com.orvid.adult.HtmlTube t : com.orvid.adult.Tubes.ALL) src.put("tube" + t.serviceId, (c, q) -> t.search(c, q, null).items.size());
        ExecutorService ex = Executors.newCachedThreadPool();
        Map<String, List<String>> res = new LinkedHashMap<>();
        for (String k : src.keySet()) res.put(k, new ArrayList<>());
        for (String q : new String[] {"gardening"}) {
            Map<String, Future<String>> fs = new LinkedHashMap<>();
            for (Map.Entry<String, S> e : src.entrySet()) {
                String c = Fetch.newCircuit(e.getKey() + q);
                fs.put(e.getKey(), ex.submit(() -> {
                    long t0 = System.currentTimeMillis();
                    try { int n = Fetch.retrying(c, cc -> e.getValue().run(cc, q)); return (System.currentTimeMillis() - t0) + "ms/" + n; }
                    catch (Exception err) { return "FAIL" + (System.currentTimeMillis() - t0) + "ms(" + String.valueOf(err.getMessage()).replaceAll("\\s+", " ").substring(0, Math.min(30, String.valueOf(err.getMessage()).length())) + ")"; }
                }));
            }
            for (Map.Entry<String, Future<String>> f : fs.entrySet()) {
                String r; try { r = f.getValue().get(60, TimeUnit.SECONDS); } catch (TimeoutException t) { r = "TIMEOUT60s"; }
                res.get(f.getKey()).add(r);
            }
            System.out.println("query done: " + q);
        }
        for (Map.Entry<String, List<String>> e : res.entrySet()) System.out.printf("%-12s %s%n", e.getKey(), String.join("  ", e.getValue()));
        Tor.get().stop(); Thread.sleep(1000); System.exit(0);
    }
}
