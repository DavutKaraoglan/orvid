import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.yt.Visionos;
import java.io.*;
import java.net.HttpURLConnection;
public class YtSpeedProbe {
    static String pull(String c, String url, long bytes) throws Exception {
        long t = System.currentTimeMillis();
        long[] first = {-1};
        int n = Fetch.on(c, () -> {
            HttpURLConnection h = Fetch.open(url);
            h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
            h.setRequestProperty("Range", "bytes=0-" + (bytes - 1));
            int code = h.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code);
            first[0] = System.currentTimeMillis() - t;
            byte[] buf = new byte[65536]; int total = 0, r;
            try (InputStream in = h.getInputStream()) {
                while (total < bytes && System.currentTimeMillis() - t < 25000 && (r = in.read(buf)) > 0) total += r;
            }
            return total;
        });
        long ms = Math.max(1, System.currentTimeMillis() - t - first[0]);
        return "first byte " + first[0] + "ms, " + (n * 1000L / 1024 / ms) + " KB/s (" + n / 1024 + " KB)";
    }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        Visionos.refill();
        Thread.sleep(4000);
        String[] queries = {"cat", "music", "news", "cooking"};
        for (String q : queries) {
            var items = com.orvid.yt.Search.first(Fetch.newCircuit("s"), q, null).items;
            for (int i = 0; i < 2 && i < items.size(); i++) {
                String id = Visionos.videoId(items.get(i).getUrl());
                String c = Visionos.takePlayCircuit();
                long t = System.currentTimeMillis();
                try {
                    Visionos.Video v = Visionos.fetch(c, id);
                    long resolve = System.currentTimeMillis() - t;
                    Visionos.Format best = null;
                    for (Visionos.Format f : v.formats) if (f.mime != null && f.mime.startsWith("video/") && f.height <= 720 && f.url != null && (best == null || f.height > best.height)) best = f;
                    if (best == null) { System.out.println(q + " " + id + " no format, hls=" + (v.hls != null)); continue; }
                    long need = best.averageBitrate / 8 / 1024;
                    System.out.println(q + " " + id + " resolve " + resolve + "ms, " + best.height + "p needs ~" + need + " KB/s");
                    System.out.println("   same circuit:  " + pull(c, best.url, 4L << 20));
                    System.out.println("   fresh circuit: " + pull(Fetch.newCircuit("fresh"), best.url, 4L << 20));
                } catch (Exception e) { System.out.println(q + " " + id + " FAIL " + e); }
            }
        }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
