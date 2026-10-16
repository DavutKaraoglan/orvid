import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.tw.Twitch;
import java.io.*;
import java.net.*;
public class TwitchProbe {
    static long t0;
    static void log(String s) { System.out.println(String.format("%6d %s", System.currentTimeMillis() - t0, s)); }
    static String get(String c, String u) throws Exception {
        return Fetch.on(c, () -> { HttpURLConnection h = Fetch.open(u); h.setRequestProperty("User-Agent", Fetch.USER_AGENT); return new String(h.getInputStream().readAllBytes()); });
    }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor ready " + Tor.get().awaitReady(180000));
        String c = Fetch.newCircuit("tw");

        t0 = System.currentTimeMillis();
        Twitch.Page p = Fetch.retrying(c, cc -> Twitch.search(cc, "minecraft", null));
        log("search " + p.items.size());
        String id = Twitch.vodId(p.items.get(0).getUrl());
        Twitch.Media m = Fetch.retrying(c, cc -> Twitch.vod(cc, id));
        log("vod token + probe");
        String master = get(c, m.hls);
        log("master " + master.length() + "B");
        String low = null, mid = null;
        String[] lines = master.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                System.out.println("   " + lines[i].replaceAll(".*BANDWIDTH=(\\d+).*RESOLUTION=([0-9x]+).*", "$1 $2"));
                if (low == null || lines[i].contains("360")) low = lines[i + 1];
                if (lines[i].contains("1280x720")) mid = lines[i + 1];
            }
        }
        for (String v : new String[]{low, mid}) {
            if (v == null) continue;
            String pl = get(c, v);
            String seg = null; String dur = null;
            for (String l : pl.split("\n")) { if (l.startsWith("#EXTINF")) dur = l; else if (!l.startsWith("#") && !l.isEmpty() && seg == null) seg = l; }
            URI base = URI.create(v);
            String segUrl = base.resolve(seg).toString();
            long t = System.currentTimeMillis();
            byte[] b = Fetch.on(c, () -> { HttpURLConnection h = Fetch.open(segUrl); return h.getInputStream().readAllBytes(); });
            log("variant " + v.replaceAll(".*/([^/]+)/index.*", "$1") + " " + dur + " first segment " + b.length / 1024 + "KB in " + (System.currentTimeMillis() - t) + "ms (single circuit)");
            long total = b.length;
            long part = (total + 2) / 3;
            t = System.currentTimeMillis();
            java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(3);
            java.util.List<java.util.concurrent.Future<Integer>> fs = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                final long from = i * part, to = Math.min(total, from + part) - 1;
                final String cc = c + "#p" + i;
                fs.add(ex.submit(() -> Fetch.on(cc, () -> { HttpURLConnection h = Fetch.open(segUrl); h.setRequestProperty("Range", "bytes=" + from + "-" + to); return h.getInputStream().readAllBytes().length; })));
            }
            int sum = 0; for (java.util.concurrent.Future<Integer> f : fs) sum += f.get();
            ex.shutdown();
            log("   same segment " + sum / 1024 + "KB in " + (System.currentTimeMillis() - t) + "ms (3 circuits in parallel)");
        }
        Tor.get().stop(); Thread.sleep(1500); System.exit(0);
    }
}
